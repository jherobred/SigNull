package app.signull.ui.model3d

import androidx.compose.ui.graphics.Color
import app.signull.core.map.Geometry
import app.signull.core.map.Pt
import kotlin.math.max
import kotlin.math.sqrt

/** A point in floor space: x and z on the ground (map x and map y), y is height. Meters. */
data class V3(val x: Float, val y: Float, val z: Float)

enum class FaceKind { FLOOR, WALL, SLAB }

/** A flat polygon. [group] ties faces to a floor or room for tapping. */
class Face3(
    val points: List<V3>,
    val fill: Color,
    val stroke: Color,
    val kind: FaceKind,
    val group: Long = 0L,
)

/** A vertical signal bar standing on the floor. */
class Pillar3(val base: V3, val height: Float, val color: Color)

class Label3(val at: V3, val text: String, val emphasized: Boolean = false)

class Scene3(
    val faces: List<Face3>,
    val pillars: List<Pillar3> = emptyList(),
    val labels: List<Label3> = emptyList(),
    val center: V3,
    val radius: Float,
)

data class Room3(val corners: List<Pt>, val height: Float, val name: String, val tint: Color)

data class Spot3(val x: Float, val y: Float, val score: Float?, val color: Color)

data class FloorLayer(
    val id: Long,
    val label: String,
    val elevation: Float,
    val outline: List<Pt>?,
    val rooms: List<List<Pt>>,
    val spots: List<Spot3>,
)

object SceneBuilder {

    /** One floor: outer walls, rooms at the ceiling height, and signal bars. */
    fun floor(
        outline: List<Pt>?,
        ceilingM: Float,
        rooms: List<Room3>,
        spots: List<Spot3>,
        wallColor: Color,
        groundColor: Color,
    ): Scene3 {
        val faces = mutableListOf<Face3>()
        val labels = mutableListOf<Label3>()
        if (outline != null && outline.size >= 3) {
            faces += Face3(outline.map { V3(it.x, 0f, it.y) }, groundColor, wallColor.copy(alpha = 0.6f), FaceKind.FLOOR)
            faces += walls(outline, 0f, ceilingM, wallColor.copy(alpha = 0.05f), wallColor.copy(alpha = 0.35f))
        }
        rooms.forEachIndexed { index, room ->
            if (room.corners.size < 3) return@forEachIndexed
            faces += Face3(room.corners.map { V3(it.x, 0.01f, it.y) }, room.tint.copy(alpha = 0.16f), room.tint.copy(alpha = 0.9f), FaceKind.FLOOR, index.toLong())
            faces += walls(room.corners, 0f, room.height, room.tint.copy(alpha = 0.2f), room.tint.copy(alpha = 0.85f), index.toLong())
            val c = Geometry.centroid(room.corners)
            labels += Label3(V3(c.x, room.height + 0.25f, c.y), room.name)
        }
        val pillars = spots.map { s ->
            Pillar3(V3(s.x, 0f, s.y), 0.3f + 2.0f * (s.score ?: 0f), s.color)
        }
        val xz = buildList {
            outline?.let { addAll(it) }
            rooms.forEach { addAll(it.corners) }
            spots.forEach { add(Pt(it.x, it.y)) }
        }
        val height = max(ceilingM, rooms.maxOfOrNull { it.height } ?: 0f)
        val (center, radius) = frame(xz, height)
        return Scene3(faces, pillars, labels, center, radius)
    }

    /** The whole building: one slab per floor, stacked by level, with each floor's signal bars. */
    fun building(
        floors: List<FloorLayer>,
        selectedId: Long?,
        accent: Color,
        neutral: Color,
        fallbackOutline: List<Pt>,
    ): Scene3 {
        val faces = mutableListOf<Face3>()
        val pillars = mutableListOf<Pillar3>()
        val labels = mutableListOf<Label3>()
        val allXz = mutableListOf<Pt>()
        floors.forEach { floor ->
            val outline = floor.outline?.takeIf { it.size >= 3 } ?: fallbackOutline
            allXz += outline
            val selected = floor.id == selectedId
            val tint = if (selected) accent else neutral
            val e = floor.elevation
            faces += Face3(
                outline.map { V3(it.x, e, it.y) },
                tint.copy(alpha = if (selected) 0.34f else 0.16f),
                tint.copy(alpha = if (selected) 1f else 0.55f),
                FaceKind.SLAB,
                floor.id,
            )
            faces += walls(outline, e - SLAB_M, e, tint.copy(alpha = if (selected) 0.4f else 0.2f), tint.copy(alpha = 0.6f), floor.id)
            floor.rooms.forEach { room ->
                if (room.size >= 3) faces += Face3(room.map { V3(it.x, e + 0.02f, it.y) }, Color.Transparent, tint.copy(alpha = 0.7f), FaceKind.FLOOR, floor.id)
            }
            floor.spots.forEach { s -> pillars += Pillar3(V3(s.x, e, s.y), 0.25f + 1.2f * (s.score ?: 0f), s.color) }
            val corner = outline.minByOrNull { it.x + it.y } ?: outline.first()
            labels += Label3(V3(corner.x, e + 0.3f, corner.y), floor.label, emphasized = selected)
        }
        val top = floors.maxOfOrNull { it.elevation } ?: 0f
        val (center, radius) = frame(allXz, top + 2f)
        return Scene3(faces, pillars, labels, center, radius)
    }

    private fun walls(polygon: List<Pt>, bottom: Float, top: Float, fill: Color, stroke: Color, group: Long = 0L): List<Face3> =
        polygon.indices.map { i ->
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            Face3(
                listOf(V3(a.x, bottom, a.y), V3(b.x, bottom, b.y), V3(b.x, top, b.y), V3(a.x, top, a.y)),
                fill,
                stroke,
                FaceKind.WALL,
                group,
            )
        }

    private fun frame(xz: List<Pt>, height: Float): Pair<V3, Float> {
        val bounds = Geometry.bounds(xz) ?: return V3(0f, height / 2f, 0f) to 10f
        val cx = bounds.centerX
        val cz = bounds.centerY
        val half = sqrt(bounds.width * bounds.width + bounds.height * bounds.height) / 2f
        return V3(cx, height / 2f, cz) to max(max(half, height), 4f)
    }

    const val SLAB_M = 0.3f
}
