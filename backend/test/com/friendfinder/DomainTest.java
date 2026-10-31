package com.friendfinder;

import java.util.*;
import com.friendfinder.model.*;
import com.friendfinder.service.MatchingService;
import com.friendfinder.util.*;

/** No test framework: fail with AssertionError when an invariant breaks. */
public final class DomainTest {
    private static int checks;
    private static void check(boolean value,String label) { checks++; if(!value)throw new AssertionError(label); }
    private static void rejects(Runnable work,String label) { boolean rejected=false; try{work.run();}catch(IllegalArgumentException|IllegalStateException|UnsupportedOperationException e){rejected=true;}check(rejected,label); }
    private static Member member(long id,Map<Long,Integer> ratings,String experience,String collaboration,String activity,Set<String> goals) {
        Member member=new Member(id,"Test Member "+id,"member"+id,"member"+id+"@example.test");
        member.setQuestionnaire(new QuestionnaireResponse(ratings,goals,experience,collaboration,activity));return member;
    }
    public static void main(String[] args) throws Exception {
        MatchingService matching=new MatchingService();
        Map<Long,Integer> ratings=Map.of(1L,5,2L,4,3L,1);
        Member first=member(1,ratings,"Intermediate","Both","Daily",Set.of("Friends","Project Partners"));
        Member similar=member(2,ratings,"Intermediate","Both","Daily",Set.of("Friends","Project Partners"));
        Member different=member(3,Map.of(1L,1,2L,1,3L,5),"Beginner","Mostly Discussion","Casual",Set.of("Mentorship"));
        check(matching.calculateMatch(first,similar)==100,"Identical engaged profiles match 100%");
        check(matching.calculateMatch(first,similar)>matching.calculateMatch(first,different),"Similar profiles score higher");
        check(matching.calculateMatch(first,different)==matching.calculateMatch(different,first),"Symmetric score");
        Random random=new Random(42);
        for(int i=0;i<250;i++) {
            Member candidate=member(4,Map.of(1L,random.nextInt(5)+1,2L,random.nextInt(5)+1,3L,random.nextInt(5)+1),List.of("Beginner","Intermediate","Advanced").get(random.nextInt(3)),"Both","Daily",Set.of("Friends"));
            int score=matching.calculateMatch(first,candidate);check(score>=0&&score<=100,"Match stays in range");
        }
        Member indifferent=member(5,Map.of(1L,1,2L,1),"Intermediate","Both","Daily",Set.of("Friends"));
        check(matching.calculateMatch(indifferent,indifferent)==50,"Shared indifference does not earn interest points");
        rejects(()->new Member("Name","!","bad"),"Invalid username rejected");
        rejects(()->first.setEmail("bad-email"),"Invalid email rejected");
        rejects(()->new QuestionnaireResponse(Map.of(1L,6),Set.of("Friends"),"Beginner","Both","Daily"),"Invalid rating rejected");
        rejects(()->first.getQuestionnaire().getRatings().put(1L,1),"Defensive immutable ratings");
        Post post=new Post(1,1,"Title","Body");check(post.getStatus().equals("PENDING"),"New post is pending");
        rejects(()->post.approve(1),"Cannot approve own post");post.approve(2);check(post.getStatus().equals("APPROVED"),"Post can be approved");rejects(()->post.reject(2),"Cannot remoderate post");
        rejects(()->new Message(1,1,"  "),"Blank message rejected");
        Conversation privateChat=new PrivateConversation(1,1,List.of(first,similar));
        Conversation groupChat=new GroupConversation(2,1,"Project team",List.of(first,similar));
        check(privateChat.getDisplayName(1).equals(similar.getFullName()),"Private polymorphic label is peer");
        check(groupChat.getDisplayName(1).equals("Project team"),"Group polymorphic label is title");
        check(new CommunityLeader(2,"Leader","leader","leader@example.test").canCreateGroup(),"Leader inherits member group behavior");
        String hash=Passwords.hash("Demo123!");check(!hash.contains("Demo123!"),"No plain text password");check(Passwords.verify("Demo123!",hash),"Password verification");check(!Passwords.verify("wrong",hash),"Wrong password rejected");check(!hash.equals(Passwords.hash("Demo123!")),"Different random salts");
        String json="{\"id\":17,\"content\":\"Hello\\n\\\"world\\\"\\u263a\",\"list\":[true,false,null,2.5]}";
        check(Json.stringify(Json.parse(json)).contains("\"id\":17,"),"Integral JSON remains integral");
        check(Json.stringify(Json.parse(Json.stringify(Json.parse(json)))).equals(Json.stringify(Json.parse(json))),"JSON round trip");
        rejects(()->Json.parse("{\"id\":1,\"id\":2}"),"Duplicate keys rejected");
        rejects(()->Json.parse("[1,]"),"Malformed JSON rejected");
        rejects(()->Json.parse("true garbage"),"Trailing JSON rejected");
        check(Json.stringify(java.sql.Timestamp.from(java.time.Instant.parse("2026-10-05T12:00:00Z"))).equals("\"2026-10-05T12:00:00Z\""),"Database timestamps serialize in UTC");
        String seed=java.nio.file.Files.readString(java.nio.file.Path.of("database/seed.sql"));
        var hashes=java.util.regex.Pattern.compile("120000:[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+").matcher(seed);
        int demoAccounts=0;
        while(hashes.find()){check(Passwords.verify("Demo123!",hashes.group()),"Seed password works with Java hashing");demoAccounts++;}
        check(demoAccounts==9,"All nine demo account password hashes checked");
        System.out.println("PASS: "+checks+" domain checks");
    }
}
