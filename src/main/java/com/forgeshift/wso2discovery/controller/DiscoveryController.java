package com.forgeshift.wso2discovery.controller;

import com.forgeshift.wso2discovery.config.DiscoveryProperties;
import com.forgeshift.wso2discovery.domain.DiscoveryJob;
import com.forgeshift.wso2discovery.domain.DiscoveryState;
import com.forgeshift.wso2discovery.dto.DiscoveryJobResponse;
import com.forgeshift.wso2discovery.dto.StartDiscoveryRequest;
import com.forgeshift.wso2discovery.repository.DiscoveryJobRepository;
import com.forgeshift.wso2discovery.service.DiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Bulk-discovery endpoints. For per-resource discovery (one resource type
 * per call) see {@link Wso2ApisController} and the future
 * Wso2&lt;Resource&gt;Controller siblings.
 *
 *   POST   /discoveries          - start a bulk discovery (fan-out)
 *   GET    /discoveries/{id}     - read job state
 *   GET    /discoveries          - list jobs (filters: state, companyName, tenant)
 *   DELETE /discoveries/{id}     - delete job document
 */
@Slf4j
@RestController
@RequestMapping("/discoveries")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final DiscoveryJobRepository jobRepository;
    private final DiscoveryProperties discoveryProps;

    @PostMapping
    public ResponseEntity<DiscoveryJobResponse> start(@Valid @RequestBody StartDiscoveryRequest req) {
        if (!StringUtils.hasText(req.getCompanyName())) {
            req.setCompanyName(discoveryProps.getDefaultCompanyName());
        }
        DiscoveryJob job = discoveryService.startDiscovery(req);
        return ResponseEntity
                .created(URI.create("/discoveries/" + job.getId()))
                .body(DiscoveryJobResponse.from(job));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiscoveryJobResponse> get(@PathVariable String id) {
        return jobRepository.findById(id)
                .map(DiscoveryJobResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Page<DiscoveryJobResponse> list(
            @RequestParam(required = false) DiscoveryState state,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String tenant,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size);
        Page<DiscoveryJob> jobs;
        if (state != null) {
            jobs = jobRepository.findByState(state, pr);
        } else if (StringUtils.hasText(companyName)) {
            jobs = jobRepository.findByCompanyName(companyName, pr);
        } else if (StringUtils.hasText(tenant)) {
            jobs = jobRepository.findByWso2Tenant(tenant, pr);
        } else {
            jobs = jobRepository.findAll(pr);
        }
        return jobs.map(DiscoveryJobResponse::from);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!jobRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        jobRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
