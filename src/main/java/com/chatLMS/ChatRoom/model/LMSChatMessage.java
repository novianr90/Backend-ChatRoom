package com.chatLMS.ChatRoom.model;

// import javax.persistence.*;
// import java.util.Date;

// @Entity
// @Table(name = "LMS_ChatMessage")
// public class LMSChatMessage {

//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     @Column(name = "LMS_ChatMessage_ID")
//     private Long id;

//     // Kolom wajib iDempiere
//     @Column(name = "AD_Client_ID", nullable = false)
//     private Integer adClientId;

//     @Column(name = "AD_Org_ID", nullable = false)
//     private Integer adOrgId;

//     @Column(name = "IsActive", nullable = false, length = 1)
//     private String isActive = "Y";

//     @Column(name = "Created", nullable = false)
//     private Date created = new Date();

//     @Column(name = "CreatedBy", nullable = false)
//     private String createdBy; // ID Pengguna/Siswa dari token ESS

//     @Column(name = "Updated", nullable = false)
//     private Date updated = new Date();

//     @Column(name = "UpdatedBy", nullable = false)
//     private String updatedBy;

//     // Kolom khusus Chat
//     @Column(name = "Room_ID")
//     private String roomId; // Misalnya ID Course atau Survey

//     @Column(name = "Message_Content", columnDefinition = "TEXT")
//     private String messageContent;

//     // Tambahkan Getter dan Setter di sini (atau gunakan @Data dari Lombok)
    
// }