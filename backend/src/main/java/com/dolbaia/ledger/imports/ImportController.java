package com.dolbaia.ledger.imports;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dolbaia.ledger.common.ApiException;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    @GetMapping
    List<ImportService.BatchSummary> latest() {
        return service.latest();
    }

    @GetMapping("/{id}")
    ImportService.BatchDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    /** Multipart field `workbook`. 201 with the new batch, or 200 with the batch that already holds this file. */
    @PostMapping
    ResponseEntity<ImportService.Uploaded> upload(@RequestParam(name = "workbook", required = false) MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("invalid_workbook", "Choose an .xlsx or .xlsm workbook.");
        }
        ImportService.Uploaded result = service.upload(file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/commit")
    ImportService.BatchSummary commit(@PathVariable UUID id) {
        return service.commit(id);
    }
}
