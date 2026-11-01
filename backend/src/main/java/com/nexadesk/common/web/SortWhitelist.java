package com.nexadesk.common.web;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Only explicitly allowed properties can be sorted on: a client must not be able to sort on (and thereby
 * probe) an arbitrary entity attribute, nor trigger a 500 with an unknown property.
 */
public final class SortWhitelist {

    private SortWhitelist() {
    }

    public static Pageable check(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_SORT,
                        "Tri non autorisé sur « " + order.getProperty() + " ». Valeurs possibles : "
                                + String.join(", ", allowedProperties.stream().sorted().toList()));
            }
        }
        return pageable;
    }
}
