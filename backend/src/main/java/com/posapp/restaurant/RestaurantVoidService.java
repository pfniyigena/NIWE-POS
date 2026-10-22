package com.posapp.restaurant;

import com.posapp.common.ListPaging;
import com.posapp.restaurant.dto.RestaurantVoidLogDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** The log of voids, comps, cancelled orders and receipt copies. */
@Service
public class RestaurantVoidService {

    private final RestaurantVoidLogRepository repository;

    public RestaurantVoidService(RestaurantVoidLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RestaurantVoidLogDto> filtered(String q, String type, String user, LocalDate from, LocalDate to) {
        return repository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(l -> type == null || type.isBlank() || type.equalsIgnoreCase("ALL") || type.equalsIgnoreCase(l.getEntryType()))
                .filter(l -> user == null || user.isBlank() || user.equalsIgnoreCase(l.getByUser()))
                .filter(l -> ListPaging.between(l.getCreatedAt(), from, to))
                .filter(l -> ListPaging.has(q, l.getDescription(), l.getReason(), l.getOrderLabel(), l.getByUser()))
                .map(RestaurantVoidLogDto::from).toList();
    }
}
