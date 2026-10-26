package com.voicereminder.vip.data.db

object SeedData {

    val categories: List<CategoryEntity> = listOf(
        CategoryEntity(name = "الاستيقاظ والنوم", colorHex = "#F2C14E", iconKey = "sun", isDefault = true),
        CategoryEntity(name = "المدرسة والدراسة", colorHex = "#5FD3C4", iconKey = "school", isDefault = true),
        CategoryEntity(name = "العمل", colorHex = "#7FB2FF", iconKey = "work", isDefault = true),
        CategoryEntity(name = "الأدوية", colorHex = "#FF8A9B", iconKey = "medication", isDefault = true),
        CategoryEntity(name = "المواعيد", colorHex = "#B9A7FF", iconKey = "event", isDefault = true),
        CategoryEntity(name = "التسوق", colorHex = "#FFB86B", iconKey = "cart", isDefault = true),
        CategoryEntity(name = "الرياضة", colorHex = "#8BE28B", iconKey = "fitness", isDefault = true),
        CategoryEntity(name = "المهام المنزلية", colorHex = "#E0A6FF", iconKey = "home", isDefault = true),
        CategoryEntity(name = "الصلاة والعبادات", colorHex = "#9AD7C7", iconKey = "prayer", isDefault = true),
        CategoryEntity(name = "تذكيرات شخصية", colorHex = "#FFD166", iconKey = "person", isDefault = true)
    )

    data class SeedTemplate(
        val key: String,
        val title: String,
        val description: String,
        val iconKey: String,
        val colorHex: String,
        val categoryName: String,
        val items: List<Pair<Int, String>>
    )

    val templates: List<SeedTemplate> = listOf(
        SeedTemplate(
            key = "wake_up",
            title = "قالب الاستيقاظ",
            description = "ثلاث رسائل متدرجة من لحظة الاستيقاظ حتى جهوزية الحقيبة.",
            iconKey = "sun",
            colorHex = "#F2C14E",
            categoryName = "الاستيقاظ والنوم",
            items = listOf(
                0 to "صباح الخير، حان وقت الاستيقاظ. افتح عينيك وابدأ يومك.",
                10 to "اذهب لغسل وجهك واستعد ليومك بهدوء.",
                25 to "حان وقت تجهيز حقيبتك وأغراضك قبل الخروج."
            )
        ),
        SeedTemplate(
            key = "school",
            title = "قالب المدرسة",
            description = "تذكير متسلسل بتجهيز الحقيبة والكتب ثم الانطلاق.",
            iconKey = "school",
            colorHex = "#5FD3C4",
            categoryName = "المدرسة والدراسة",
            items = listOf(
                0 to "حان وقت تجهيز حقيبة المدرسة.",
                15 to "تأكد من الكتب والأدوات والواجبات.",
                30 to "حان وقت الذهاب إلى المدرسة."
            )
        ),
        SeedTemplate(
            key = "work",
            title = "قالب العمل",
            description = "من الاستعداد إلى التحقق من مواعيد اليوم.",
            iconKey = "work",
            colorHex = "#7FB2FF",
            categoryName = "العمل",
            items = listOf(
                0 to "استعد للذهاب إلى العمل.",
                10 to "لا تنسَ أغراضك ومفاتيحك وبطاقتك.",
                20 to "تحقق من مواعيد اليوم قبل الانطلاق."
            )
        ),
        SeedTemplate(
            key = "daily_tasks",
            title = "قالب المهام اليومية",
            description = "تذكيرات خفيفة للماء والترتيب ومراجعة الغد.",
            iconKey = "tasks",
            colorHex = "#B9A7FF",
            categoryName = "المهام المنزلية",
            items = listOf(
                0 to "اشرب كوب ماء الآن.",
                60 to "رتّب غرفتك وأعد كل شيء إلى مكانه.",
                120 to "راجع مهام الغد واكتب أهم ثلاث مهام."
            )
        ),
        SeedTemplate(
            key = "sleep",
            title = "قالب النوم",
            description = "روتين هادئ قبل النوم مع تجهيز ملابس الغد.",
            iconKey = "night",
            colorHex = "#9AD7C7",
            categoryName = "الاستيقاظ والنوم",
            items = listOf(
                0 to "استعد للنوم وأبعد الشاشات عنك.",
                15 to "جهز ملابس الغد وأغراضك.",
                25 to "اضبط منبه الصباح، ونم مبكرًا لتصحو أفضل."
            )
        )
    )
}
