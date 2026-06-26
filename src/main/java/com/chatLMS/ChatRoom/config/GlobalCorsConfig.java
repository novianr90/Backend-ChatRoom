// package com.chatLMS.ChatRoom.config;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.web.cors.CorsConfiguration;
// import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// import org.springframework.web.filter.CorsFilter;

// @Configuration
// public class GlobalCorsConfig {

//     @Bean
//     public CorsFilter corsFilter() {
//         CorsConfiguration config = new CorsConfiguration();
        
//         // 1. Izinkan semua origin (Vue di localhost:8189, dsb)
//         config.addAllowedOriginPattern("*"); 
        
//         // 2. Izinkan semua jenis request (GET, POST, OPTIONS, dll)
//         config.addAllowedMethod("*"); 
        
//         // 3. Izinkan semua header
//         config.addAllowedHeader("*"); 
        
//         // 4. Matikan credentials jika menggunakan Origin *
//         config.setAllowCredentials(false); 

//         UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//         // Terapkan aturan ini ke SELURUH endpoint API
//         source.registerCorsConfiguration("/**", config); 
        
//         return new CorsFilter(source);
//     }
// }