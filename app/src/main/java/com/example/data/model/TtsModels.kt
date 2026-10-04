package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class TtsVoice(
    val id: String,
    val name: String,
    val banglaTitle: String,
    val description: String,
    val languageCode: String,
    val styleBadge: String,
    val isCustomFromApi: Boolean = false
)

@Immutable
data class TtsModelOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
)

@Immutable
data class TtsLanguageOption(
    val code: String,
    val labelBangla: String,
    val labelEnglish: String,
    val flagEmoji: String
)

enum class TtsEngineMode(val titleBangla: String, val titleEnglish: String) {
    AUTO_SMART("Cartesia Sonic AI (ক্লাউড)", "Cartesia Sonic AI"),
    CARTESIA_CLOUD("Cartesia Strict Mode", "Cartesia Strict"),
    DEVICE_NATIVE("অন-ডিভাইস ভয়েস (Device TTS)", "On-Device TTS")
}

@Immutable
data class PresetScript(
    val id: String,
    val titleBangla: String,
    val category: String,
    val languageCode: String,
    val transcript: String
)

object TtsDefaults {
    val defaultVoices = listOf(
        TtsVoice(
            id = "694f9389-aac1-45b6-b726-9d9369183238",
            name = "Friendly Narrator",
            banglaTitle = "ফ্রেন্ডলি ন্যারেটর (স্পষ্ট ও প্রাণবন্ত)",
            description = "Expressive, natural conversational tone for Bangla & English storytelling and narration.",
            languageCode = "multilingual",
            styleBadge = "Best for Bangla"
        ),
        TtsVoice(
            id = "a0e99841-438c-4a64-b679-ae501e7d6091",
            name = "Barbershop Man",
            banglaTitle = "বার্বারশপ ম্যান (গভীর পুরুষ কণ্ঠ)",
            description = "Warm, resonant male studio voice ideal for narration, podcasts, and announcements.",
            languageCode = "multilingual",
            styleBadge = "Studio Male"
        ),
        TtsVoice(
            id = "79a125e8-cd45-4c13-8a67-188112f4dd22",
            name = "British Lady",
            banglaTitle = "ব্রিটিশ লেডি (মার্জিত নারী কণ্ঠ)",
            description = "Crisp, articulate female voice suited for audiobooks, news reading, and tutorials.",
            languageCode = "multilingual",
            styleBadge = "Articulate Female"
        ),
        TtsVoice(
            id = "b7d50908-b17c-442d-ad8d-810c63997ed9",
            name = "California Girl",
            banglaTitle = "ক্যালিফোর্নিয়া গার্ল (তরুণ ও প্রাণবন্ত)",
            description = "Upbeat, energetic voice great for social media reels, promos, and casual dialogue.",
            languageCode = "multilingual",
            styleBadge = "Energetic"
        ),
        TtsVoice(
            id = "d46abd1d-2d02-43e8-819f-51fb652c1c61",
            name = "Newsman",
            banglaTitle = "নিউজম্যান (সংবাদ উপস্থাপক)",
            description = "Authoritative broadcast cadence designed for breaking news and documentary scripts.",
            languageCode = "multilingual",
            styleBadge = "Broadcast"
        ),
        TtsVoice(
            id = "41534e16-2966-4c6b-9670-111411def906",
            name = "Wise Guide",
            banglaTitle = "ওয়াইজ গাইড (শান্ত ও গম্ভীর)",
            description = "Calm, composed vocal timbre suitable for meditation, poetry, and educational lessons.",
            languageCode = "multilingual",
            styleBadge = "Calm & Deep"
        )
    )

    val models = listOf(
        TtsModelOption(
            id = "sonic-3.6",
            title = "Sonic 3.6 Multilingual",
            subtitle = "Latest flagship model with native Bangla (বাংলা) & 44-language support",
            badge = "Best • Bangla"
        ),
        TtsModelOption(
            id = "sonic-3",
            title = "Sonic 3.0 Expressive",
            subtitle = "High-emotion expressive synthesis with native multilingual support",
            badge = "Expressive"
        ),
        TtsModelOption(
            id = "sonic-turbo",
            title = "Sonic Turbo",
            subtitle = "Ultra-low latency voice generation for instant responses",
            badge = "Ultra Fast"
        ),
        TtsModelOption(
            id = "sonic-2",
            title = "Sonic 2.0 Studio",
            subtitle = "Classic studio voice model for English & general scripts",
            badge = "Classic"
        )
    )

    val languages = listOf(
        TtsLanguageOption("bn", "বাংলা (Bangla)", "Bangla", "🇧🇩"),
        TtsLanguageOption("auto", "অটো ডিটেক্ট (Auto)", "Auto-Detect", "🌐"),
        TtsLanguageOption("en", "ইংরেজি (English)", "English", "🇺🇸"),
        TtsLanguageOption("hi", "হিন্দি (Hindi)", "Hindi", "🇮🇳"),
        TtsLanguageOption("ar", "আরবি (Arabic)", "Arabic", "🇸🇦"),
        TtsLanguageOption("es", "স্প্যানিশ (Spanish)", "Spanish", "🇪🇸"),
        TtsLanguageOption("fr", "ফরাসি (French)", "French", "🇫🇷"),
        TtsLanguageOption("ja", "জাপানি (Japanese)", "Japanese", "🇯🇵")
    )

    val presetScripts = listOf(
        PresetScript(
            id = "bn_welcome",
            titleBangla = "স্বাগতম বার্তা (বাংলা)",
            category = "বাংলা • Greeting",
            languageCode = "bn",
            transcript = "নমস্কার এবং স্বাগতম! সনিক ভয়েস টেক্সট টু স্পিচ স্টুডিওতে আপনাকে স্বাগত জানাই। এখানে আপনি যেকোনো লেখা মুহূর্তের মধ্যেই স্পষ্ট ও প্রাণবন্ত কণ্ঠে রূপান্তর করতে পারবেন।"
        ),
        PresetScript(
            id = "bn_news",
            titleBangla = "সংবাদ পাঠ (News)",
            category = "বাংলা • Broadcast",
            languageCode = "bn",
            transcript = "সন্ধ্যা সাতটার বিশেষ সংবাদে স্বাগতম। আজ প্রযুক্তি বিশ্বে কৃত্রিম বুদ্ধিমত্তা এক নতুন দিগন্তের সূচনা করেছে। এখন মানুষ নিজের ভাষায় আরও নিখুঁত ও স্বাভাবিক কণ্ঠে অডিও তৈরি করতে পারছে।"
        ),
        PresetScript(
            id = "bn_story",
            titleBangla = "গল্পের আসর (Story)",
            category = "বাংলা • Storytelling",
            languageCode = "bn",
            transcript = "অনেক দিন আগের কথা। নির্জন পাহাড়ের পাদদেশে এক ছোট্ট গ্রামে বাস করত এক তরুণ অভিযাত্রী। প্রতিদিন ভোরে সে নতুন কোনো রহস্যের খোঁজে বেরিয়ে পড়ত।"
        ),
        PresetScript(
            id = "en_studio",
            titleBangla = "AI Studio Intro (EN)",
            category = "English • Podcast",
            languageCode = "en",
            transcript = "Welcome to SonicVoice Studio, powered by Cartesia Sonic 3.6 AI. Experience ultra-fast, deeply expressive text-to-speech synthesis with studio-grade audio clarity."
        ),
        PresetScript(
            id = "en_story",
            titleBangla = "Sci-Fi Narration (EN)",
            category = "English • Audiobook",
            languageCode = "en",
            transcript = "Beyond the horizon of the neon skyline, a quiet signal echoed through the starlight. Every word carried the warmth of human emotion across lightyears of silence."
        )
    )
}
