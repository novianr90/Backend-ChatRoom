package com.chatLMS.ChatRoom.controller;

import com.chatLMS.ChatRoom.model.CreateGroupRequest;
import com.chatLMS.ChatRoom.model.Group;
import com.chatLMS.ChatRoom.model.User;
import com.chatLMS.ChatRoom.model.Message;

// Tambahkan import untuk Service penyimpanan kita
import com.chatLMS.ChatRoom.service.ChatDbService; 

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

@Controller
@CrossOrigin("*")
@RequestMapping("/api")
public class ChatController {
    
    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;
    
    // INJEKSI SERVICE PENYIMPANAN ASYNC DI SINI
    @Autowired
    private ChatDbService chatDbService; 
    private RestTemplate restTemplate = new RestTemplate();

    private final ArrayList<User> users = new ArrayList<>();
    private final HashMap<String, Group> groups = new HashMap<>() {{
        put("public", new Group("public", "Public", new HashSet<>()));
    }};
    private final HashMap<String, String> sessionTracker = new HashMap<>();
    private final String ID_EMP_URL = "http://192.168.9.75:8083/api/v1";

    /**
     * Register a user
     * @param user The user to register
     */
    @PostMapping("/register")
    public ResponseEntity<String> RegisterUser(@RequestBody User user) {
        boolean isAlreadyRegistered = users.stream()
                .anyMatch(u -> u.getUsername().equals(user.getUsername()));

        if (isAlreadyRegistered) {
            return ResponseEntity.ok().build();
        }
        users.add(user);
        groups.get("public").AddMember(simpMessagingTemplate, user);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove a user
     * @param user The user to remove
     */
    @PostMapping("/unregister")
    public ResponseEntity<String> UnregisterUser(@RequestBody User user) {
        RemoveUserFromGroups(user);
        users.remove(user);
        return ResponseEntity.ok().build();
    }

    /**
     * Get all users
     * @return All users in the server
     */
    @RequestMapping("/users")
    @ResponseBody
    public ArrayList<User> GetUsers() {
        return users;
    }

    // @RequestMapping("/get_groups/user")
    // @ResponseBody
    // public ArrayList<Group> GetGroupsOfUser(@RequestBody User user) {
    //     System.out.println("DEBUG: Mencari grup untuk user: " + user.getUsername());
    //     ArrayList<Group> result = new ArrayList<>();
        
    //     groups.forEach((key, group) -> {
    //         // KUNCI PERBAIKAN: Bandingkan berdasarkan teks 'username', bukan memori objek
    //         boolean isMember = group.getMembers().stream()
    //                 .anyMatch(m -> m.getUsername().equals(user.getUsername()));
            
    //         if (isMember) {
    //             result.add(group);
    //         }
    //     });
        
    //     System.out.println("DEBUG: Grup ditemukan untuk user " + user.getUsername() + ": " + result.size());
    //     return result;
    // }


    // @CrossOrigin(origins = "*", allowedHeaders = "*")
    @PostMapping("/get_groups/user")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> GetGroupsOfUser(
            @RequestHeader("Authorization") String token) {
        try {
            // 1. Siapkan Request Header & Payload kosong "{}"
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);
            
            // Map kosong akan otomatis diubah menjadi JSON kosong "{}" oleh Spring
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(new HashMap<>(), headers);

            // 2. Tembak API IDempiere
            String idempiereUrl = ID_EMP_URL + "/processes/getchatroom";
            ResponseEntity<Map> response = restTemplate.postForEntity(idempiereUrl, requestEntity, Map.class);

            List<Map<String, Object>> vueGroups = new ArrayList<>();
            System.out.println("DEBUG: Menerima response dari IDempiere untuk grup user: " + response);
            // 3. Ekstrak data dari string "summary"
            if (response.getBody() != null && !((Boolean) response.getBody().get("isError"))) {
                String summaryStr = (String) response.getBody().get("summary");
                ObjectMapper mapper = new ObjectMapper();
                JsonNode summaryJson = mapper.readTree(summaryStr);
                JsonNode chatRooms = summaryJson.get("chat_rooms");

                // Mapping ke format yang dikenali oleh Vue
                if (chatRooms != null && chatRooms.isArray()) {
                    for (JsonNode room : chatRooms) {
                        System.out.println("DEBUG: Memproses chat room - ID: " + room);
                        Map<String, Object> groupInfo = new HashMap<>();
                        groupInfo.put("id", String.valueOf(room.get("HRM_ChatRoom_ID").asInt()));
                        groupInfo.put("name", room.get("chat_room_name").asText());
                        groupInfo.put("type", room.get("room_type").asText()); 
                        vueGroups.add(groupInfo);
                    }
                }
            }
            return ResponseEntity.ok(vueGroups);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    // =========================================================================
    // 2. API: MENGAMBIL RIWAYAT PESAN DARI IDEMPIERE
    // =========================================================================
    @CrossOrigin(origins = "*", allowedHeaders = "*")
    @GetMapping("/get_history/{roomId}")
    @ResponseBody
    public ResponseEntity<List<Message>> getChatHistory(
            @RequestHeader("Authorization") String token,
            @PathVariable String roomId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // Perhatian:
            // - $top=1 dihapus agar menarik SEMUA history, bukan cuma 1 pesan terakhir.
            // - Ditambahkan &$orderby=Created asc agar urutan chat dari yang terlama ke terbaru.
            String idempiereUrl = ID_EMP_URL + "/models/HRM_ChatMessage" +
                                  "?$filter=IsActive eq true AND HRM_ChatRoom_ID eq " + roomId + 
                                  "&$select=Username,Message,Name,Created&$orderby=Created";
            
            ResponseEntity<Map> response = restTemplate.exchange(idempiereUrl, HttpMethod.GET, requestEntity, Map.class);

            List<Message> history = new ArrayList<>();
            
            // Format respon Model GET IDempiere biasanya berisi array di dalam "records"
            if (response.getBody() != null && response.getBody().containsKey("records")) {
                List<Map<String, Object>> records = (List<Map<String, Object>>) response.getBody().get("records");
                
                for (Map<String, Object> record : records) {
                    Message msg = new Message();
                    System.out.println("DEBUG: Memproses record chat - Username: " + record);
                    msg.setSenderName((String) record.get("Name"));
                    msg.setMessage((String) record.get("Message"));
                    msg.setReceiverId(roomId);
                    // msg.setName((String) record.get("Name"));

                    if (record.get("Created") != null) {
                        // Biasanya IDempiere mengembalikan format "2026-06-10 16:00:00"
                        msg.setDate(record.get("Created").toString()); 
                    }
                    // msg.setStatus("MESSAGE"); // Bebas disesuaikan jika Vue butuh status
                    history.add(msg);
                }
            }
            
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
        }
    }

    /**
     * Given a group id return the group
     * @return The group associated with the id
     */
    @RequestMapping("/get_group")
    @ResponseBody
    public Group GetGroupById(@RequestBody String id) {
        return groups.get(id.substring(0, id.length() - 1));
    }

    /**
     * Function to handle a private message sent
     * @param message The message sent
     */
    // @MessageMapping("/private-message")
    // public Message ReceivePrivateMessage(@Payload Message message) {
    //     try {
    //         // ---------------------------------------------------------
    //         // 1. SIMPAN KE DATABASE IDEMPIERE VIA LATAR BELAKANG (ASYNC)
    //         // ---------------------------------------------------------
    //         // Catatan: Pastikan method getter di bawah ini (misal: getSenderName, getMessage) 
    //         // sesuai dengan nama variabel yang ada di dalam model "Message.java" bawaan repo.
    //         chatDbService.saveMessageToIDempiere(
    //             message.getSenderName(), // ID atau Nama pengirim
    //             message.getReceiverId(), // ID Room/Group tujuan
    //             message.getMessage()     // Isi teks obrolan
    //         );

    //         // ---------------------------------------------------------
    //         // 2. BROADCAST PESAN KE FRONTEND SEPERTI BIASA
    //         // ---------------------------------------------------------
    //         Group group = groups.get(message.getReceiverId());
    //         if (group != null) {
    //             group.SendMessageToMembers(simpMessagingTemplate, message);
    //         }
            
    //         return message;
    //     } catch (Exception e) {
    //         System.err.println("Error saat memproses WebSocket message: " + e.getMessage());
    //         throw new RuntimeException(e);
    //     }
    // }
    @MessageMapping("/private-message")
    public Message ReceivePrivateMessage(
            @Payload Message message, 
            @Header("Authorization") String token) { // Tangkap token dari STOMP Vue
        try {
            Map<String, Object> idempiereRequest = new HashMap<>();
            idempiereRequest.put("HRM_ChatRoom_ID", Integer.parseInt(message.getReceiverId())); 
            idempiereRequest.put("Message", message.getMessage());

            // Siapkan Header yang berisi Token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(idempiereRequest, headers);

            String idempiereUrl = ID_EMP_URL + "/processes/sendmessage";
            ResponseEntity<Map> response = restTemplate.postForEntity(idempiereUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                simpMessagingTemplate.convertAndSend("/topic/room/" + message.getReceiverId(), message);
            }

            return message;
        } catch (Exception e) {
            System.err.println("❌ Gagal mengirim pesan ke IDempiere: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove a specific user from all groups
     * @param user The user to remove
     */
    private void RemoveUserFromGroups(User user) {
        ArrayList<String> toDeleted = new ArrayList<>();
        groups.forEach((key, group) -> {
            group.RemoveMember(simpMessagingTemplate, user);
            if (group.getMembers().size() == 0 && !Objects.equals(key, "public")) {
                toDeleted.add(key);
            }
        });
        toDeleted.forEach(groups::remove);
    }

    @CrossOrigin(origins = "*", allowedHeaders = "*") // Tambahkan ini agar aman dari CORS
    @PostMapping("/new_group")
    @ResponseBody // Tambahkan ini agar mengembalikan String biasa, bukan mencari file HTML
    // public String createNewGroup(@RequestBody Map<String, Object> payload) {
    //     // 1. Ambil data dari Frontend
    //     String groupName = (String) payload.get("groupName");
    //     List<String> usernames = (List<String>) payload.get("usernames");

    //     // 2. Buat UUID unik untuk ID ruangan
    //     String newRoomId = "room_" + UUID.randomUUID().toString().substring(0, 8);

    //     // 3. Masukkan member ke dalam grup
    //     HashSet<User> groupMembers = new HashSet<>();
    //     for (String name : usernames) {
    //         groupMembers.add(new User(name)); 
    //     }

    //     // 4. Simpan grup ke dalam memori
    //     Group newGroup = new Group(newRoomId, groupName, groupMembers);
    //     groups.put(newRoomId, newGroup);

    //     System.out.println("✅ Grup baru dibuat: " + groupName + " | ID: " + newRoomId + " | Jumlah Member: " + groupMembers.size());

    //     // 5. Kembalikan ID tersebut ke Frontend agar Vue bisa langsung membuka chat-nya
    //     return newRoomId; 
    // }
    public String createNewGroup(
            @RequestHeader("Authorization") String token, // Tangkap token dari Vue
            @RequestBody Map<String, Object> payload) {
        try {
            System.out.println("DEBUG: Menerima request buat grup baru dengan payload: " + payload);
            String groupName = (String) payload.get("groupName");
            List<String> usernamesList = (List<String>) payload.get("usernames");
            String memberUsernames = String.join(",", usernamesList);
            String roomType = usernamesList.size() > 2 ? "GROUP" : "PERSONAL";

            Map<String, Object> idempiereRequest = new HashMap<>();
            idempiereRequest.put("room_name", groupName);
            idempiereRequest.put("room_type", roomType);
            idempiereRequest.put("member_usernames", memberUsernames);

            // Siapkan Header yang berisi Token untuk dikirim ke IDempiere
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token); // Oper token ke IDempiere

            // Bungkus request body dan header menjadi satu entitas
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(idempiereRequest, headers);
            System.out.println("DEBUG: Mengirim request ke IDempiere dengan body: " + idempiereRequest + " dan header: " + headers);
            String idempiereUrl = ID_EMP_URL + "/processes/createchatroom";
            // Gunakan requestEntity yang sudah ada headernya
            ResponseEntity<Map> response = restTemplate.postForEntity(idempiereUrl, requestEntity, Map.class);
            System.out.println("DEBUG: Menerima response dari IDempiere: " + response);
            if (response.getBody() != null && !((Boolean) response.getBody().get("isError"))) {
                String summaryStr = (String) response.getBody().get("summary");
                ObjectMapper mapper = new ObjectMapper();
                JsonNode summaryJson = mapper.readTree(summaryStr);
                String newRoomId = String.valueOf(summaryJson.get("HRM_ChatRoom_ID").asInt());

                Map<String, Object> roomNotification = new HashMap<>();
                roomNotification.put("id", newRoomId);
                roomNotification.put("name", groupName);
                roomNotification.put("type", roomType);

                // Kirim sinyal ke setiap member yang terdaftar di grup ini
                for (String memberUsername : usernamesList) {
                    // Mengirim ke jalur pribadi masing-masing member (ex: /topic/user/Citra_ESS)
                    simpMessagingTemplate.convertAndSend("/topic/user/" + memberUsername, roomNotification);
                    System.out.println("📡 WebSocket: Mengirim info room baru ke user " + memberUsername);
                }
                // 👆 BATAS KODE BARU 👆

                return newRoomId;
            } else {
                throw new RuntimeException("IDempiere mengembalikan error saat membuat grup.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    @CrossOrigin(origins = "*", allowedHeaders = "*")
    @PostMapping("/read_chat")
    public ResponseEntity<String> markChatAsRead(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> payload) {
        try {
            // 1. Ambil ID Ruangan dari Vue (pastikan formatnya angka)
            Integer roomId = Integer.parseInt(payload.get("roomId").toString());

            // 2. Siapkan Payload untuk IDempiere
            Map<String, Object> idempiereRequest = new HashMap<>();
            idempiereRequest.put("HRM_ChatRoom_ID", roomId);

            // 3. Sisipkan Token ke Header
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(idempiereRequest, headers);

            // 4. Tembak API IDempiere
            String idempiereUrl = ID_EMP_URL + "/processes/readchat";
            ResponseEntity<Map> response = restTemplate.postForEntity(idempiereUrl, requestEntity, Map.class);

            // 5. Cek Response
            if (response.getBody() != null && !((Boolean) response.getBody().get("isError"))) {
                System.out.println("✅ Status read berhasil diupdate di IDempiere untuk Room: " + roomId);
                return ResponseEntity.ok("OK");
            } else {
                throw new RuntimeException("IDempiere mengembalikan error saat update read status.");
            }
        } catch (Exception e) {
            System.err.println("❌ Gagal update read status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        // Ambil nama dari header yang dikirim Vue tadi
        List<String> usernameHeader = accessor.getNativeHeader("username");
        
        if (usernameHeader != null && !usernameHeader.isEmpty()) {
            String connectedUser = usernameHeader.get(0);
            sessionTracker.put(sessionId, connectedUser); // Simpan ke radar
            System.out.println("📡 RADAR: " + connectedUser + " terhubung (Session ID: " + sessionId + ")");
        }
    }

    // 3. RADAR KELUAR: Mendeteksi saat tab browser ditutup / internet mati
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        // Cari tahu siapa yang barusan putus berdasarkan Session ID-nya
        String disconnectedUser = sessionTracker.get(sessionId);
        
        if (disconnectedUser != null) {
            System.out.println("📡 RADAR: " + disconnectedUser + " menutup aplikasi. Membersihkan memori...");
            
            // Buat objek dummy untuk menghapus user tersebut dari semua memori grup
            User userObj = new User(disconnectedUser);
            
            // Panggil fungsi pembersih bawaan Anda
            RemoveUserFromGroups(userObj); 
            users.removeIf(u -> u.getUsername().equals(disconnectedUser));
            
            // Hapus dari radar
            sessionTracker.remove(sessionId);
        }
    }

}