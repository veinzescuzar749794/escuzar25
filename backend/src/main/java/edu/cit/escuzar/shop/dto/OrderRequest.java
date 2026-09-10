package edu.cit.escuzar.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderRequest(
        @NotBlank String productId,
        @Min(1) int quantity
) {
}
