# Class diagram

This diagram reflects Java inheritance, interfaces, and object references. Persistence relationships represented by IDs are listed separately below.

```mermaid
classDiagram
    class User {
        <<abstract>>
        -long id
        -String fullName
        -String username
        -String email
        -QuestionnaireResponse questionnaire
        +getRole() String
        +getDashboard() String
        +setEmail(String)
        +setUsername(String)
    }
    class Member {
        +Member(String,String,String)
        +Member(long,String,String,String)
        +canCreateGroup() boolean
    }
    class CommunityLeader {
        +canModerate(Community) boolean
    }
    class Admin
    User <|-- Member
    Member <|-- CommunityLeader
    User <|-- Admin
    User "1" --> "0..1" QuestionnaireResponse
    class QuestionnaireResponse {
        -Map ratings
        -Set goals
        -String experience
        -String collaboration
        -String activity
    }
    class Interest {
        -long id
        -String name
    }
    class Community {
        -long id
        -String name
        -long leaderId
    }
    class CommunityMembership {
        -Member member
        -Community community
    }
    CommunityMembership --> Member
    CommunityMembership --> Community
    class Conversation {
        <<abstract>>
        -long id
        -long creatorId
        -List participants
        +getType() String
        +getDisplayName(long) String
    }
    class PrivateConversation
    class GroupConversation {
        -String name
    }
    Conversation <|-- PrivateConversation
    Conversation <|-- GroupConversation
    Conversation "1" --> "2..20" Member : participants
    class Approvable {
        <<interface>>
        +approve(long)
        +reject(long)
    }
    class Post {
        -long id
        -long authorId
        -long communityId
        -String title
        -String body
        -String status
        -Long moderatorId
    }
    Approvable <|.. Post
    class Reportable {
        <<interface>>
        +generateReport() Map
    }
    class DailyReport {
        -LocalDate date
        +DailyReport()
        +DailyReport(LocalDate)
    }
    Reportable <|.. DailyReport
    ReportService ..> Reportable
    MatchingService ..> User
    MessageService ..> Conversation
    PostService ..> Post
    class Message {
        -long conversationId
        -long senderId
        -String body
    }
    class Comment {
        -long postId
        -long authorId
        -String body
    }
    class FriendRequest {
        -long id
        -long senderId
        -long receiverId
        -String status
    }
    class ActivityLog {
        -long userId
        -String event
        -Long communityId
    }
```

Persistent associations use foreign-key IDs rather than loading an entire object graph: User ↔ Interest via user_interests; User ↔ FriendRequest ↔ User; Community ↔ Post; Post ↔ Comment; Conversation ↔ Message; Conversation ↔ User via conversation_members; ActivityLog ↔ actor and optional community. See the database ER diagram for cardinalities.

Constructor overloading is visible in Member/Post/DailyReport. Method overloading is in UserDAO's two searchUsers signatures. Conversation title selection is exercised through a base-class reference in MessageService, making it a concise live demonstration of runtime dispatch.
