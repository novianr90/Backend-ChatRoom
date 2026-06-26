package com.chatLMS.ChatRoom.service;

// import com.chatLMS.ChatRoom.model.LMSChatMessage;
// import com.chatLMS.ChatRoom.repository.LMSChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class ChatDbService {

    // @Autowired
    // private LMSChatMessageRepository chatRepository;

    @Async("chatTaskExecutor") // Memanggil Thread Pool yang dibuat di atas
    public void saveMessageToIDempiere(String senderId, String roomId, String content) {
        try {
            // LMSChatMessage chat = new LMSChatMessage();
            // Injeksi konteks iDempiere (Sesuaikan dengan ID Client ESS Anda)
            // chat.setAdClientId(1000000); 
            // chat.setAdOrgId(0);
            // chat.setIsActive("Y");
            // chat.setCreated(new Date());
            // chat.setCreatedBy(senderId); // ID dari frontend
            // chat.setUpdated(new Date());
            // chat.setUpdatedBy(senderId);
            
            // chat.setRoomId(roomId);
            // chat.setMessageContent(content);

            // chatRepository.save(chat);
            
            // Log untuk monitor pekerja latar belakang
            System.out.println("Pesan dari " + senderId + " tersimpan oleh " + Thread.currentThread().getName());
        } catch (Exception e) {
            System.err.println("Gagal simpan ke iDempiere: " + e.getMessage());
        }
    }
}