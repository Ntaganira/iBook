/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.dto
 * - File      : WorkflowForm.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Approval-workflow form DTO record
 * </pre>
 */
package com.ntaganira.heritier.ibook.dto;

import jakarta.validation.constraints.*;

public record WorkflowForm(
        @NotBlank(message = "{set.workflow.name.required}") String name,
        @NotBlank(message = "{set.workflow.module.required}") String module,
        @NotBlank(message = "{set.workflow.trigger.required}") String triggerEvent,
        @Min(value = 1, message = "{set.workflow.steps.invalid}")
        @Max(value = 10, message = "{set.workflow.steps.invalid}") int steps,
        @Min(value = 0, message = "{set.workflow.days.invalid}")
        @Max(value = 30, message = "{set.workflow.days.invalid}") int daysEach,
        boolean active,
        String description) {

    public WorkflowForm {
        if (steps < 1) steps = 1;
    }
}