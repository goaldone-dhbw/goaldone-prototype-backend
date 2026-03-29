package de.goaldone.backend.controller;

import de.goaldone.backend.api.RecurringTemplatesApi;
import de.goaldone.backend.model.CreateRecurringTemplateRequest;
import de.goaldone.backend.model.RecurringTemplatePage;
import de.goaldone.backend.model.RecurringTemplateResponse;
import de.goaldone.backend.model.UpdateRecurringTemplateRequest;
import de.goaldone.backend.service.RecurringTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RecurringTemplateController extends BaseController implements RecurringTemplatesApi {

    private final RecurringTemplateService recurringTemplateService;

    @Override
    public ResponseEntity<RecurringTemplatePage> listRecurringTemplates(Integer page, Integer size) {
        RecurringTemplatePage result = recurringTemplateService.listTemplates(getCurrentUserId(), getCurrentOrgId(), page, size);
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<RecurringTemplateResponse> createRecurringTemplate(
        CreateRecurringTemplateRequest createRecurringTemplateRequest) {
        RecurringTemplateResponse result = recurringTemplateService.createTemplate(createRecurringTemplateRequest, getCurrentUserId(), getCurrentOrgId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Override
    public ResponseEntity<RecurringTemplateResponse> getRecurringTemplate(UUID templateId) {
        RecurringTemplateResponse result = recurringTemplateService.getTemplate(templateId, getCurrentUserId(), getCurrentOrgId());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<RecurringTemplateResponse> updateRecurringTemplate(
        UUID templateId,
        UpdateRecurringTemplateRequest updateRecurringTemplateRequest) {
        RecurringTemplateResponse result = recurringTemplateService.updateTemplate(templateId, updateRecurringTemplateRequest, getCurrentUserId(), getCurrentOrgId());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<Void> deleteRecurringTemplate(UUID templateId) {
        recurringTemplateService.deleteTemplate(templateId, getCurrentUserId(), getCurrentOrgId());
        return ResponseEntity.noContent().build();
    }
}
