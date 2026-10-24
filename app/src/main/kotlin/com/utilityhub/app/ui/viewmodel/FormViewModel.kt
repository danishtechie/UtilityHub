package com.utilityhub.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.utilityhub.app.tools.form.FormOutput
import com.utilityhub.app.tools.form.FormTool
import com.utilityhub.logic.Validation
import com.utilityhub.logic.spec.FieldValues
import com.utilityhub.logic.spec.defaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FormState(val values: Map<String, String>, val output: FormOutput? = null, val error: String? = null)

/** Form tools are instant, pure and free, so they run synchronously and never touch the entitlement ledger. */
class FormViewModel(private val tool: FormTool) : ViewModel() {
    private val _state = MutableStateFlow(FormState(tool.fields.defaultValues()))
    val state: StateFlow<FormState> = _state.asStateFlow()

    fun setValue(key: String, value: String) = _state.update { it.copy(values = it.values + (key to value)) }

    fun run() {
        when (val r = tool.run(FieldValues(_state.value.values))) {
            is Validation.Valid -> _state.update { it.copy(output = r.value, error = null) }
            is Validation.Invalid -> _state.update { it.copy(output = null, error = r.message) }
        }
    }
}
