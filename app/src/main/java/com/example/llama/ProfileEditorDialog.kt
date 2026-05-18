package com.example.llama

import android.app.AlertDialog
import android.content.Context
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileEditorDialog(
    private val context: Context,
    private val currentProfile: UserProfile,
    private val onSave: (UserProfile) -> Unit
) {
    fun show() {
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val ageInput = EditText(context).apply {
            hint = "Age"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            text = currentProfile.age.toString().takeIf { it != "0" }?.toEditable() ?: null
        }
        view.addView(ageInput)

        val educationLevels = listOf("High School", "Bachelor", "Master", "PhD", "Other")
        val educationSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, educationLevels)
            val index = educationLevels.indexOf(currentProfile.educationLevel)
            if (index >= 0) setSelection(index)
        }
        view.addView(TextView(context).apply { text = "Education Level" })
        view.addView(educationSpinner)

        val workingAreasOptions = listOf("Software", "Hardware", "AI/ML", "Data Science", "Education", "Healthcare", "Finance", "Other")
        val checkedItems = BooleanArray(workingAreasOptions.size) { i ->
            currentProfile.workingAreas.contains(workingAreasOptions[i])
        }
        val workingAreasTextView = TextView(context).apply { text = "Working Areas (tap to select)" }
        view.addView(workingAreasTextView)
        workingAreasTextView.setOnClickListener {
            showMultiSelectDialog(workingAreasOptions, checkedItems) { selected ->
                // update later
            }
        }

        val yearsInput = EditText(context).apply {
            hint = "Years of Experience"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            text = currentProfile.yearsExperience.toString().takeIf { it != "0" }?.toEditable() ?: null
        }
        view.addView(yearsInput)

        val otherInfoInput = EditText(context).apply {
            hint = "Other Information"
            text = currentProfile.otherInfo.toEditable()
        }
        view.addView(otherInfoInput)

        MaterialAlertDialogBuilder(context)
            .setTitle("Edit Profile")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val age = ageInput.text.toString().toIntOrNull() ?: 0
                val education = educationSpinner.selectedItem.toString()
                val years = yearsInput.text.toString().toIntOrNull() ?: 0
                val otherInfo = otherInfoInput.text.toString()
                val selectedWorkingAreas = workingAreasOptions.filterIndexed { index, _ -> checkedItems[index] }
                onSave(UserProfile(age, education, selectedWorkingAreas, years, otherInfo))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMultiSelectDialog(items: List<String>, checked: BooleanArray, onResult: (BooleanArray) -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Select Working Areas")
            .setMultiChoiceItems(items.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("OK") { _, _ -> onResult(checked) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun String.toEditable() = android.text.Editable.Factory.getInstance().newEditable(this)
}