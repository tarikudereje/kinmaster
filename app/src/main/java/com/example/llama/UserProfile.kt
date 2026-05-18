package com.example.llama

data class UserProfile(
    var age: Int = 0,
    var educationLevel: String = "",
    var workingAreas: List<String> = emptyList(),
    var yearsExperience: Int = 0,
    var otherInfo: String = ""
) {
    fun toJson(): String = """
        {
            "age": $age,
            "educationLevel": "$educationLevel",
            "workingAreas": ${workingAreas.joinToString(separator = "\", \"", prefix = "[\"", postfix = "\"]")},
            "yearsExperience": $yearsExperience,
            "otherInfo": "$otherInfo"
        }
    """.trimIndent()

    companion object {
        fun fromJson(json: String): UserProfile? = try {
            val age = Regex("\"age\": (\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 0
            val educationLevel = Regex("\"educationLevel\": \"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
            val workingAreasRaw = Regex("\"workingAreas\": \\[([^\\]]+)\\]").find(json)?.groupValues?.get(1) ?: ""
            val workingAreas = if (workingAreasRaw.isNotEmpty()) {
                workingAreasRaw.split(", ").map { it.trim('"') }
            } else emptyList()
            val yearsExperience = Regex("\"yearsExperience\": (\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 0
            val otherInfo = Regex("\"otherInfo\": \"([^\"]*)\"").find(json)?.groupValues?.get(1) ?: ""
            UserProfile(age, educationLevel, workingAreas, yearsExperience, otherInfo)
        } catch (e: Exception) { null }
    }
}