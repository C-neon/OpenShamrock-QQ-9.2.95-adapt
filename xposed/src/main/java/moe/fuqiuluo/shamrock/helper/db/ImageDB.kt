package moe.fuqiuluo.shamrock.helper.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import mqq.app.MobileQQ


@Entity(tableName = "image_mapping")
data class ImageMapping (
    @PrimaryKey
    val fileName: String,
    val chatType: Int,
    val size: Long,
    /** 图片所属会话(群号/好友uin)，用于构造图片下载请求 */
    val peerUin: String = "",
    /** QQ 图片服务器使用的文件标识(uuid) */
    val fileUuid: String = "",
    /** QQ 自己给出的原图地址(可能为空) */
    val originUrl: String = "",
    /** QQ 本地已缓存的图片路径(可能为空) */
    val sourcePath: String = "",
    /** 收到消息的时间(unix 秒)，作为群图下载协议的 msgTime */
    val msgTime: Long = 0,
    /** 发送者 uin，部分下载协议需要 secondUin */
    val senderUin: String = ""
)

@Dao
interface ImageMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(mapping: ImageMapping)

    @Query("SELECT * FROM image_mapping WHERE fileName = :fileName")
    fun queryByFileName(fileName: String): ImageMapping?
}

// 缓存表，版本升级时直接重建即可
@Database(entities = [ImageMapping::class], version = 3)
internal abstract class ImageDB: RoomDatabase() {
    abstract fun imageMappingDao(): ImageMappingDao

    companion object {
        private const val DB_NAME = "image_mapping.db"

        @Volatile
        private var instance: ImageDB? = null

        fun getInstance(): ImageDB {
            if (instance == null) {
                synchronized(ImageDB::class) {
                    if (instance == null) {
                        ImageDB
                        instance = Room.databaseBuilder(MobileQQ.getContext(), ImageDB::class.java, DB_NAME)
                            .fallbackToDestructiveMigration()
                            .build()
                    }
                }
            }
            return instance!!
        }
    }
}
