package com.feetfit.server.service.DrugService;

import com.feetfit.server.domain.drug.DrugProduct;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.repository.drug.DrugProductRepository;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class DrugCatalogStore {
    private final DrugProductRepository products;
    private final DrugCatalogMapper mapper;
    private final EntityManager entityManager;

    public void saveProduct(DrugCategory category, DrugResponseDTO.DrugItemDTO item, int displayOrder) {
        saveProducts(category, List.of(item), displayOrder);
    }

    public void saveProducts(DrugCategory category, List<DrugResponseDTO.DrugItemDTO> items, int displayOrder) {
        if (items.isEmpty()) {
            return;
        }
        Map<String, DrugProduct> existing = new HashMap<>();
        products.findForImport(items.stream().map(DrugResponseDTO.DrugItemDTO::getId).toList())
                .forEach(product -> existing.put(product.getId(), product));
        entityManager.unwrap(org.hibernate.Session.class).setJdbcBatchSize(50);
        for (DrugResponseDTO.DrugItemDTO item : items) {
            DrugProduct product = existing.get(item.getId());
            boolean isNew = product == null;
            if (isNew) {
                product = DrugProduct.builder().id(item.getId()).build();
                existing.put(item.getId(), product);
            }
            product.updateProduct(category, item.getName(), item.getCompany(), item.getDescription(),
                    item.getCategory(), Boolean.TRUE.equals(item.getOnline()), displayOrder++, mapper.serialize(item));
            if (isNew) {
                entityManager.persist(product);
            }
        }
    }

    public void savePermit(String id, DrugResponseDTO.PermitInfoDTO permit) {
        products.findById(id).orElseThrow().updatePermit(mapper.serialize(permit));
    }

    public void saveRecall(String id, DrugResponseDTO.RecallInfoDTO recall) {
        products.findById(id).orElseThrow().updateRecall(mapper.serialize(recall));
    }

    public void saveDetails(String id, boolean permitFetched, DrugResponseDTO.PermitInfoDTO permit,
                             DrugResponseDTO.RecallInfoDTO recall) {
        if (!permitFetched && recall == null) {
            return;
        }
        DrugProduct product = products.findById(id).orElseThrow();
        if (permitFetched) {
            product.updatePermit(mapper.serialize(permit));
        }
        if (recall != null) {
            if ("unavailable".equals(recall.getStatus()) && product.getRecallPayload() != null) {
                var previous = mapper.deserialize(product.getRecallPayload(), DrugResponseDTO.RecallInfoDTO.class);
                recall = DrugResponseDTO.RecallInfoDTO.builder()
                        .status("unavailable").records(previous.getRecords())
                        .checkedAt(previous.getCheckedAt()).sourceUrl(previous.getSourceUrl()).build();
            }
            product.updateRecall(mapper.serialize(recall));
        }
        refreshEligibility(product);
    }

    public void refreshEligibility() {
        entityManager.unwrap(org.hibernate.Session.class).setJdbcBatchSize(50);
        products.findAll().forEach(this::refreshEligibility);
    }

    private void refreshEligibility(DrugProduct product) {
        var permit = mapper.deserialize(product.getPermitPayload(), DrugResponseDTO.PermitInfoDTO.class);
        var recall = mapper.toRecall(product);
        product.updateRecommendationEligibility(product.isOnline()
                || (permit != null && !permit.isCancelled() && "clear".equals(recall.getStatus())));
    }
}
