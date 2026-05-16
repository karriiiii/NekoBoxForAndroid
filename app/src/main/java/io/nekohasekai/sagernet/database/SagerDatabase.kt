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
                        
                        // Формируем списки доменов и IP точно так же, как их вводил бы пользователь в UI (через перенос строки)
                        val domainsAds = "geosite:category-ads-all\nmax.ru\noneme.ru\napi.oneme.ru"
                        
                        val domainsTg = "geosite:telegram\ntelegram.org\nt.me"
                        
                        val domainsRu = "geosite:vk\ngeosite:yandex\nkeyword:yandex\nkeyword:yastatic\nkeyword:yadi.sk\nkeyword:xn--80aswg\nkeyword:xn--d1acpjx3f.xn--p1ai\nkeyword:xn--c1avg\nkeyword:xn--80asehdb\nkeyword:xn--p1acf\nkeyword:xn--p1ai\nkeyword:gstatic.com\nkeyword:tineye\nkeyword:vk.com\nkeyword:userapi.com\nkeyword:vk-cdn.me\nkeyword:mvk.com\nkeyword:vk-cdn.net\nkeyword:vk-portal.net\nkeyword:vk.cc\nkeyword:tradingview\ndomain:ru\ndomain:su\ndomain:by"
                        
                        val ipsRu = "geoip:private\ngeoip:ru\ngeoip:by"

                        // 1. Правило: Блокировка рекламы (outbound = -2)
                        // userOrder = 0 (Самый высокий приоритет, срабатывает первым)
                        db.execSQL("""
                            INSERT INTO rules 
                            (name, config, userOrder, enabled, domains, ip, port, sourcePort, network, source, protocol, outbound, packages) 
                            VALUES 
                            ('Блокировка рекламы', '', 0, 1, '$domainsAds', '', '', '', '', '', '', -2, '')
                        """)

                        // 2. Правило: Telegram через прокси (outbound = 0)
                        // userOrder = 1
                        db.execSQL("""
                            INSERT INTO rules 
                            (name, config, userOrder, enabled, domains, ip, port, sourcePort, network, source, protocol, outbound, packages) 
                            VALUES 
                            ('Telegram (Proxy)', '', 1, 1, '$domainsTg', '', '', '', '', '', '', 0, '')
                        """)

                        // 3. Правило: RU Сегмент напрямую (outbound = -1)
                        // userOrder = 2
                        db.execSQL("""
                            INSERT INTO rules 
                            (name, config, userOrder, enabled, domains, ip, port, sourcePort, network, source, protocol, outbound, packages) 
                            VALUES 
                            ('RU Сегмент (Direct)', '', 2, 1, '$domainsRu', '$ipsRu', '', '', '', '', '', -1, '')
                        """)
                    }
                })
                // --- КОНЕЦ ДОБАВЛЕНИЯ ----
                
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
