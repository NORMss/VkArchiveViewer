package ru.normno.vkarchivereader.data.source

import ru.normno.vkarchivereader.core.Cp1251
import ru.normno.vkarchivereader.data.repository.ArchiveRepository.Companion.PAGE_SIZE

/**
 * A fully in-memory, fake VK archive so anyone can explore the app without having
 * exported their own data first. It synthesizes exactly the HTML shape the real
 * exporter produces (see VK_ARCHIVE_STRUCTURE.md) and encodes it to windows-1251,
 * so it flows through the very same parser/repository as a real archive — the
 * chat list, pagination, search, media gallery and face grouping all work on it.
 *
 * Photos point at stable public placeholder services (portraits and scenery) so
 * the gallery and face-grouping demos load real images over the network.
 */
class DemoArchiveSource : ArchiveSource {
    override val displayName: String = "Демо-архив ВКонтакте"

    private val files: Map<String, ByteArray> = DemoArchive.build()

    override suspend fun exists(path: String): Boolean = files.containsKey(path)

    override suspend fun readBytes(path: String): ByteArray? = files[path]

    override suspend fun listDirs(path: String): List<String> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return files.keys.asSequence()
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.substring(prefix.length).substringBefore('/') }
            .distinct()
            .toList()
    }
}

// --- Demo content generation -------------------------------------------------

private data class DemoAuthor(val name: String, val link: String)

private data class DemoAtt(val description: String, val url: String)

private data class DemoMsg(
    val id: Int,
    /** null = sent by the archive owner ("Вы"). */
    val author: DemoAuthor?,
    val date: String,
    val text: String,
    val photos: List<String> = emptyList(),
    val extra: List<DemoAtt> = emptyList(),
)

private data class DemoChat(
    val peerId: String,
    val title: String,
    /** Newest message first, matching the real archive page order. */
    val messages: List<DemoMsg>,
)

private object DemoArchive {

    // Stable placeholder images. randomuser.me returns the same face for a given
    // index (so repeated people cluster in face grouping); picsum gives scenery.
    private fun man(i: Int) = "https://randomuser.me/api/portraits/men/$i.jpg"
    private fun woman(i: Int) = "https://randomuser.me/api/portraits/women/$i.jpg"
    private fun pic(id: Int) = "https://picsum.photos/id/$id/900/700.jpg"

    private val anna = DemoAuthor("Анна Смирнова", "https://vk.com/id553001")
    private val maxim = DemoAuthor("Максим Петров", "https://vk.com/id281044")
    private val olga = DemoAuthor("Ольга Кузнецова", "https://vk.com/id771233")
    private val dmitry = DemoAuthor("Дмитрий Волков", "https://vk.com/id334512")
    private val memes = DemoAuthor("Мемы и котики", "https://vk.com/club45223011")
    private val news = DemoAuthor("VK Новости", "https://vk.com/club22822305")

    fun build(): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        files["index.html"] = indexHtml().toBytes()
        val chats = demoChats()
        files["messages/index-messages.html"] = peersHtml(chats).toBytes()
        for (chat in chats) {
            val pages = chat.messages.chunked(PAGE_SIZE).ifEmpty { listOf(emptyList()) }
            pages.forEachIndexed { i, page ->
                files["messages/${chat.peerId}/messages${i * PAGE_SIZE}.html"] = pageHtml(chat, page).toBytes()
            }
        }
        return files
    }

    private fun String.toBytes(): ByteArray = Cp1251.encode(this)

    private fun demoChats(): List<DemoChat> = listOf(
        annaChat(),
        friendsChat(),
        memesChat(),
        maximChat(),
        newsChat(),
    )

    // --- Individual chats ----------------------------------------------------

    private fun annaChat(): DemoChat {
        var id = 200_000
        val m = mutableListOf<DemoMsg>()
        fun me(date: String, text: String, photos: List<String> = emptyList(), extra: List<DemoAtt> = emptyList()) =
            m.add(DemoMsg(id++, null, date, text, photos, extra))
        fun her(date: String, text: String, photos: List<String> = emptyList(), extra: List<DemoAtt> = emptyList()) =
            m.add(DemoMsg(id++, anna, date, text, photos, extra))

        // newest first
        her("7 авг 2026 в 21:14:03", "Спасибо за сегодня! Было очень здорово &#128522;")
        me("7 авг 2026 в 21:10:41", "Тебе спасибо, что выбралась. Повторим на выходных?")
        her("7 авг 2026 в 20:58:12", "Смотри какой закат был с набережной", listOf(pic(1015), pic(1016)))
        me("7 авг 2026 в 18:30:05", "Уже подхожу к кофейне, займу столик у окна")
        her("7 авг 2026 в 18:22:47", "Выхожу через 10 минут")
        me("6 авг 2026 в 12:03:19", "Кинул тебе на почту билеты, проверь пожалуйста")
        her("6 авг 2026 в 11:40:52", "Отправила заявку на отпуск, ура! &#127881;")
        her("2 авг 2026 в 19:05:33", "Новый рецепт, получилось нереально вкусно", listOf(pic(1080)))
        me("2 авг 2026 в 19:01:10", "Выглядит потрясающе, научишь?")
        her("28 июл 2026 в 09:12:44", "С добрым утром! Держи котика для настроения", listOf(pic(219)))
        me("27 июл 2026 в 22:47:01", "Спокойной ночи &#128564;")
        her("27 июл 2026 в 22:31:58", "Моя новая аватарка, как тебе?", listOf(woman(44)))
        me("25 июл 2026 в 15:20:26", "Погнали в горы в сентябре?")
        her("25 июл 2026 в 15:02:09", "Я только за!! Уже начала смотреть маршруты", listOf(pic(1036), pic(1039)))
        me("20 июл 2026 в 10:15:37", "Доброе утро! Как спалось?")
        her("14 июл 2026 в 20:44:12", "Смотри, нашла нашу старую фотку с моря", listOf(woman(44), pic(1040)))
        me("14 июл 2026 в 20:40:00", "Ого, сколько лет прошло! Классное было лето")
        her("2 июл 2026 в 13:22:31", "Отправила тебе документ по проекту", extra = listOf(DemoAtt("Файл", "https://example.com/plan.pdf")))
        me("2 июл 2026 в 13:00:14", "Спасибо, гляну вечером")
        her("18 июн 2026 в 08:05:59", "Доброе утро &#9749;", listOf(pic(225)))
        me("10 июн 2026 в 19:33:20", "Привет! Давно не виделись, как ты?")
        her("10 июн 2026 в 19:30:41", "Привет-привет! Всё отлично, соскучилась &#128522;")

        return DemoChat(peerId = "553001", title = "Анна Смирнова", messages = m)
    }

    private fun maximChat(): DemoChat {
        var id = 300_000
        val m = mutableListOf<DemoMsg>()
        fun me(date: String, text: String, photos: List<String> = emptyList(), extra: List<DemoAtt> = emptyList()) =
            m.add(DemoMsg(id++, null, date, text, photos, extra))
        fun him(date: String, text: String, photos: List<String> = emptyList(), extra: List<DemoAtt> = emptyList()) =
            m.add(DemoMsg(id++, maxim, date, text, photos, extra))

        him("5 авг 2026 в 23:41:12", "Погоняли завтра в футбол? Собираемся в 19:00")
        me("5 авг 2026 в 23:30:08", "Я в деле, беру мяч")
        him("3 авг 2026 в 14:12:55", "Смотри какую я собрал клавиатуру", listOf(pic(0), pic(3)))
        me("3 авг 2026 в 14:05:40", "Красота! Сколько по деньгам вышло?")
        him("30 июл 2026 в 18:20:33", "Скинул тебе трек, послушай обязательно", extra = listOf(DemoAtt("Аудиозапись", "https://example.com/track.mp3")))
        me("28 июл 2026 в 11:11:11", "С днём рождения, дружище! &#127881;")
        him("28 июл 2026 в 11:30:02", "Спасибо огромное! Ждём на праздник в субботу")
        him("22 июл 2026 в 09:47:18", "Утренняя пробежка, 8 км!", listOf(man(32)))
        me("22 июл 2026 в 09:50:00", "Ну ты машина &#128170;")
        him("15 июл 2026 в 20:15:44", "Держи мем в тему", listOf(pic(1062)))
        me("15 июл 2026 в 20:10:26", "Ахаха, отправляю дальше")
        him("8 июл 2026 в 16:33:09", "Го в кино на новинку в пятницу?")
        me("8 июл 2026 в 16:20:51", "Давай, беру билеты")
        him("1 июл 2026 в 12:00:00", "Как продвигается проект?")
        me("1 июл 2026 в 12:04:37", "Потихоньку, скоро релиз")
        him("21 июн 2026 в 21:05:19", "Классно посидели на даче", listOf(man(32), pic(1043), pic(1050)))
        me("21 июн 2026 в 21:00:02", "Да, шашлык был топ! Повторим")
        him("12 июн 2026 в 10:30:44", "Привет! Ты как на выходных, свободен?")
        me("12 июн 2026 в 10:35:12", "Привет! В субботу да, а что задумал?")

        return DemoChat(peerId = "281044", title = "Максим Петров", messages = m)
    }

    private fun friendsChat(): DemoChat {
        var id = 400_000
        val m = mutableListOf<DemoMsg>()
        fun add(a: DemoAuthor?, date: String, text: String, photos: List<String> = emptyList(), extra: List<DemoAtt> = emptyList()) =
            m.add(DemoMsg(id++, a, date, text, photos, extra))

        add(olga, "8 авг 2026 в 10:02:41", "Всем привет! Кто идёт на пикник в это воскресенье? &#127881;")
        add(maxim, "8 авг 2026 в 10:05:13", "Я точно буду, беру гитару")
        add(null, "8 авг 2026 в 10:07:55", "И я! Захвачу плед и фрисби")
        add(anna, "8 авг 2026 в 10:12:30", "Ура! Я сделаю салаты. Вот прошлогодние фотки с пикника", listOf(woman(44), man(32), woman(68)))
        add(dmitry, "8 авг 2026 в 10:20:04", "Приду с семьёй. Погода обещает быть шикарной", listOf(man(12)))
        add(olga, "6 авг 2026 в 19:44:18", "Скинула локацию, встречаемся у главного входа в парк", extra = listOf(DemoAtt("Ссылка", "https://maps.example.com/park")))
        add(null, "6 авг 2026 в 19:50:22", "Принято, буду вовремя")
        add(dmitry, "4 авг 2026 в 13:15:47", "Ребят, кто-нибудь смотрел новый сезон? Без спойлеров!")
        add(maxim, "4 авг 2026 в 13:20:09", "Смотрел, молчу-молчу &#128519;")
        add(anna, "2 авг 2026 в 21:33:51", "Наша команда после турнира &#127942;", listOf(woman(44), man(32), man(12), woman(68)))
        add(olga, "2 авг 2026 в 21:40:12", "Лучший день! Спасибо всем")
        add(null, "2 авг 2026 в 21:45:03", "Горжусь нами! В следующем году берём кубок")
        add(dmitry, "30 июл 2026 в 08:22:37", "Доброе утро, команда! Всем продуктивного дня &#9749;", listOf(man(12)))
        add(maxim, "27 июл 2026 в 18:05:29", "Кто в теннис в выходные?", listOf(man(32)))
        add(anna, "27 июл 2026 в 18:10:44", "Я бы сыграла, давно не брала ракетку")
        add(olga, "24 июл 2026 в 12:00:15", "Поздравляем Диму с новой работой! &#127881;", listOf(woman(68)))
        add(dmitry, "24 июл 2026 в 12:05:41", "Спасибо, друзья! Очень тронут")
        add(null, "24 июл 2026 в 12:10:08", "Заслуженно! Обмываем в пятницу")
        add(anna, "18 июл 2026 в 20:30:52", "Вечер настолок у меня дома, кто за?", listOf(woman(44)))
        add(maxim, "18 июл 2026 в 20:33:19", "Я за! Принесу закуски")
        add(olga, "15 июл 2026 в 09:12:03", "Всем хороших выходных! Держите котика", listOf(pic(200)))
        add(dmitry, "10 июл 2026 в 22:14:36", "Собрались на рыбалку, красота какая", listOf(man(12), pic(1051), pic(1058)))
        add(null, "10 июл 2026 в 22:20:11", "Эх, жаль пропустил. В следующий раз с вами")
        add(anna, "3 июл 2026 в 16:40:27", "Нашла кафе с лучшим кофе в городе &#9749;", listOf(woman(44), pic(225)))
        add(olga, "3 июл 2026 в 16:45:50", "Надо сходить всем вместе!")
        add(maxim, "25 июн 2026 в 19:00:44", "Всем привет! Создал беседу, чтобы не терять друг друга &#128522;")
        add(null, "25 июн 2026 в 19:02:10", "Отличная идея! Всех обнял")

        return DemoChat(peerId = "2000000042", title = "Друзья &#127881;", messages = m)
    }

    private fun memesChat(): DemoChat {
        var id = 500_000
        val m = mutableListOf<DemoMsg>()
        val captions = listOf(
            "Когда пятница наступила раньше плана &#128514;",
            "Настроение на сегодня",
            "Кто спит так же? &#128571;",
            "Понедельник, ты ли это",
            "Котик дня &#128049;",
            "Мем, который слишком точный",
            "Держите позитив на вечер &#128522;",
            "Когда наконец-то выходные &#127881;",
            "Уровень уюта: максимальный",
            "Это мы после обеда",
            "Пёсель просто хороший мальчик &#128054;",
            "Осеннее настроение уже близко &#127810;",
        )
        val pics = listOf(
            237, 1025, 219, 1062, 40, 42, 250, 349, 431, 512,
            659, 823, 1084, 1074, 1059, 1039, 1024, 1011, 996, 981,
            977, 959, 940, 200, 433, 628, 766, 837, 1043, 1069,
        )
        // 56 posts -> spans two pages (50 per page), exercising pagination.
        var day = 8
        var month = 8
        var hour = 20
        var minute = 55
        for (i in 0 until 56) {
            val date = "$day ${monthAbbr(month)} 2026 в $hour:${pad(minute)}:12"
            m.add(
                DemoMsg(
                    id = id++,
                    author = memes,
                    date = date,
                    text = captions[i % captions.size],
                    photos = listOf(pic(pics[i % pics.size])),
                )
            )
            // step backwards in time so page 0 stays newest-first
            minute -= 7
            if (minute < 0) { minute += 60; hour -= 2 }
            if (hour < 6) { hour = 22; day -= 1 }
            if (day < 1) { day = 28; month -= 1 }
        }
        return DemoChat(peerId = "-45223011", title = "Мемы и котики", messages = m)
    }

    private fun newsChat(): DemoChat {
        var id = 600_000
        val m = mutableListOf<DemoMsg>()
        fun post(date: String, text: String, photos: List<String> = emptyList(), extra: List<DemoAtt> = emptyList()) =
            m.add(DemoMsg(id++, news, date, text, photos, extra))

        post("7 авг 2026 в 12:00:00", "&#128241; Вышло крупное обновление приложения: тёмная тема и новый поиск.", listOf(pic(0)))
        post("5 авг 2026 в 18:30:00", "Итоги недели: самое интересное в одном посте.", extra = listOf(DemoAtt("Ссылка", "https://vk.com/press")))
        post("1 авг 2026 в 09:15:00", "&#127881; Нам исполнилось много лет! Спасибо, что вы с нами.", listOf(pic(1043)))
        post("28 июл 2026 в 14:20:00", "Совет дня: как навести порядок в личных данных.", listOf(pic(48)))
        post("20 июл 2026 в 11:00:00", "Новая функция историй уже доступна всем пользователям.", listOf(pic(60)))
        post("12 июл 2026 в 16:45:00", "Подборка полезных горячих клавиш.", extra = listOf(DemoAtt("Документ", "https://example.com/shortcuts.pdf")))
        post("3 июл 2026 в 10:05:00", "Мы обновили политику конфиденциальности. Читайте подробности.", extra = listOf(DemoAtt("Ссылка", "https://vk.com/privacy")))

        return DemoChat(peerId = "-22822305", title = "VK Новости", messages = m)
    }

    // --- HTML rendering (matches VkArchiveParser expectations) ----------------

    private fun indexHtml(): String = buildString {
        append("<!DOCTYPE html>\n")
        append("<html><head><meta charset=\"windows-1251\"><title>Архив ВКонтакте — демо</title></head>\n")
        append("<body><h1>Демонстрационный архив</h1>")
        append("<p>Это встроенные демо-данные VK Archive Reader.</p></body></html>")
    }

    private fun peersHtml(chats: List<DemoChat>): String = buildString {
        append("<!DOCTYPE html>\n")
        append("<html><head><meta charset=\"windows-1251\"><title>Сообщения</title></head><body>\n")
        for (c in chats) {
            append("<div class=\"message-peer\"><div class=\"message-peer--id\">")
            append("<a href=\"${c.peerId}/messages0.html\">${c.title}</a>")
            append("</div></div>\n")
        }
        append("</body></html>")
    }

    private fun pageHtml(chat: DemoChat, page: List<DemoMsg>): String = buildString {
        append("<!DOCTYPE html>\n")
        append("<html><head><meta charset=\"windows-1251\"></head><body>\n")
        append("<div class=\"_header_inner\"><div class=\"ui_crumb\">Сообщения</div>")
        append("<div class=\"ui_crumb\">${chat.title}</div></div>\n")
        for (msg in page) {
            append("<div class=\"message\" data-id=\"${msg.id}\">\n")
            append("  <div class=\"message__header\">")
            if (msg.author != null) {
                append("<a href=\"${msg.author.link}\">${msg.author.name}</a>, ")
            }
            append(msg.date)
            append("</div>\n")
            append("  <div>\n    ")
            append(msg.text.replace("\n", "<br>"))
            val atts = msg.photos.map { DemoAtt("Фотография", it) } + msg.extra
            if (atts.isNotEmpty()) {
                append("\n    <div class=\"kludges\">\n")
                for (a in atts) {
                    append("      <div class=\"attachment\">")
                    append("<div class=\"attachment__description\">${a.description}</div>")
                    append("<a class='attachment__link' href='${a.url}'>${a.url}</a>")
                    append("</div>\n")
                }
                append("    </div>\n")
            }
            append("\n  </div>\n")
            append("</div>\n")
        }
        append("<div class=\"pagination\"></div>\n")
        append("</body></html>")
    }

    private val monthsAbbr = arrayOf(
        "янв", "фев", "мар", "апр", "май", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    private fun monthAbbr(month: Int) = monthsAbbr[month - 1]

    private fun pad(n: Int) = if (n < 10) "0$n" else "$n"
}
