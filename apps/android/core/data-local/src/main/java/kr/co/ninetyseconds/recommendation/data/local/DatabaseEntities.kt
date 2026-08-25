package kr.co.ninetyseconds.recommendation.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
internal data class ProjectEntity(
    @PrimaryKey val projectId: String,
    val configVersion: Long,
    val defaultLanguage: String,
)

@Entity(
    tableName = "locations",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["projectId"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
internal data class LocationEntity(
    @PrimaryKey val locationId: String,
    val projectId: String,
    val code: String,
    val title: String,
    val imageRef: String?,
    val capacity: Int?,
    val enabled: Boolean,
)

@Entity(
    tableName = "recommendation_items",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["projectId"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocationEntity::class, parentColumns = ["locationId"], childColumns = ["locationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("projectId"), Index("locationId")],
)
internal data class RecommendationItemEntity(
    @PrimaryKey val itemId: String,
    val projectId: String,
    val locationId: String,
    val title: String,
    val imageRef: String?,
    val enabled: Boolean,
)

@Entity(
    tableName = "item_emotions",
    primaryKeys = ["itemId", "emotionCode"],
    foreignKeys = [ForeignKey(entity = RecommendationItemEntity::class, parentColumns = ["itemId"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("emotionCode")],
)
internal data class ItemEmotionEntity(val itemId: String, val emotionCode: String)

@Entity(
    tableName = "recommendation_events",
    indices = [Index(value = ["requestId"], unique = true), Index("decidedAtEpochMillis")],
)
internal data class RecommendationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: String,
    val itemId: String,
    val locationId: String,
    val source: String,
    val decidedAtEpochMillis: Long,
)
