package com.anuj.notesai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response returned after successful authentication, containing a JWT token. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
}
