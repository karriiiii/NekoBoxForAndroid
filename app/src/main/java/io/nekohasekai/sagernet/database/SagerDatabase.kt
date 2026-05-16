package io.nekohasekai.sagernet.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.matrix.roomigrant.GenerateRoomMigrations
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.gson.GsonConverters
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(
    entities = [ProxyGroup::class, ProxyEntity::class, RuleEntity::class],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
@TypeConverters(value = [KryoConverters::class, GsonConverters::class])
@GenerateRoomMigrations
abstract class SagerDatabase : RoomDatabase() {

    companion object {
        @OptIn(DelicateCoroutinesApi::class)
        @Suppress("EXPERIMENTAL_API_USAGE")
        val instance by lazy {
            SagerNet.application.getDatabasePath(Key.DB_PROFILE).parentFile?.mkdirs()
            Room.databaseBuilder(SagerNet.application, SagerDatabase::class.java, Key.DB_PROFILE)
//                .addMigrations(*SagerDatabase_Migrations.build())
                .setJournalMode(JournalMode.TRUNCATE)
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .setQueryExecutor { GlobalScope.launch { it.run() } }
                
                // --- ДОБАВЛЯЕМ ПРАВИЛА ПРИ ПЕРВОМ ЗАПУСКЕ ---
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        
                        // Твой кастомный JSON для поля config
                        val customConfig = "{\"rules\":[{\"domain\":[\"max.ru\",\"oneme.ru\",\"api.oneme.ru\"],\"geosite\":[\"category-ads-all\"],\"outbound\":\"block\"},{\"domain\":[\"telegram.org\",\"t.me\"],\"geosite\":[\"telegram\"],\"outbound\":\"proxy\"},{\"domain_keyword\":[\"yandex\",\"yastatic\",\"yadi.sk\",\"xn--80aswg\",\"xn--d1acpjx3f.xn--p1ai\",\"xn--c1avg\",\"xn--80asehdb\",\"xn--p1acf\",\"xn--p1ai\",\"gstatic.com\",\"tineye\",\"vk.com\",\"userapi.com\",\"vk-cdn.me\",\"mvk.com\",\"vk-cdn.net\",\"vk-portal.net\",\"vk.cc\",\"tradingview\"],\"domain_suffix\":[\".ru\",\".su\",\".by\"],\"geoip\":[\"private\",\"ru\",\"by\"],\"geosite\":[\"vk\",\"yandex\"],\"outbound\":\"direct\"}]}"
                        
                        // Вставляем запись в таблицу rules
                        db.execSQL("""
                            INSERT INTO rules 
                            (name, config, userOrder, enabled, domains, ip, port, sourcePort, network, source, protocol, outbound, packages) 
                            VALUES 
                            ('Обход RU (По умолчанию)', '$customConfig', 0, 1, '', '', '', '', '', '', '', 0, '')
                        """)
                    }
                })
                // --- КОНЕЦ ДОБАВЛЕНИЯ ---
                
                .build()
        }

        val groupDao get() = instance.groupDao()
        val proxyDao get() = instance.proxyDao()
        val rulesDao get() = instance.rulesDao()

    }

    abstract fun groupDao(): ProxyGroup.Dao
    abstract fun proxyDao(): ProxyEntity.Dao
    abstract fun rulesDao(): RuleEntity.Dao

}
