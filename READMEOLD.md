<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [Spring Security](#spring-security)
    - [Why it works](#why-it-works)
    - [How to prevent it](#how-to-prevent-it)
    - [CSRF in my project](#csrf-in-my-project)
    - [Override default Spring Security settings](#override-default-spring-security-settings)
      - [Config Class](#config-class)
    - [UserDetailsService](#userdetailsservice)
    - [AuthenticationProvider](#authenticationprovider)
    - [MyUserDetailsService](#myuserdetailsservice)
    - [UserRepo](#userrepo)
    - [UserPrincipal](#userprincipal)
    - [Summary of what we did to authenticate user credentials:](#summary-of-what-we-did-to-authenticate-user-credentials)
  - [Spring Security Bcrypt Password Encoder](#spring-security-bcrypt-password-encoder)
  - [JWT and why do we need it?](#jwt-and-why-do-we-need-it)
  - [Spring Security Project Setup for JWT](#spring-security-project-setup-for-jwt)
  - [Generating JWT Token](#generating-jwt-token)
    - [How do we sign the token?](#how-do-we-sign-the-token)
  - [Validating JWT Token](#validating-jwt-token)
  - [Spring Security Google and GitHub Login](#spring-security-google-and-github-login)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

http://bit.ly/4u5aYFY 

# Spring Security

A `Cross-Site Request Forgery` (CSRF) attack does not steal or read the session ID held in the cookie. Instead, it forces the user's browser to send an authenticated request using the active session that the browser is already storing.
Here is exactly how it works at a glance:
* The Setup: You are logged into a vulnerable website (e.g., your bank), and your browser stores your active session ID in a cookie.
* The Trap: You visit a malicious website or click a rigged link sent by an attacker.
* The Execution: The malicious site triggers a background request to the vulnerable website (e.g., a money transfer).
* The Browser's Role: Because your browser automatically includes all associated cookies (including your session ID) with every request to that domain, the vulnerable website thinks you intentionally authorized the action.

### Why it works
Websites rely on cookies to know who is making a request. The server sees the valid session ID in the cookie and processes the request, assuming it came from the user's deliberate intent, unaware that the request originated from a malicious third-party site. [1, 2, 3, 4, 5]

### How to prevent it
To stop this, developers use a few standard security measures:
* Anti-CSRF Tokens: The server issues a unique, secret, and unpredictable token for the user's session. Any critical action (like submitting a form) must include this token. Because the attacker's site cannot read data from your legitimate session, it cannot guess this token. [1, 2, 3, 4]

### CSRF in my project

Within the file `src/main/java/com/amitkapila/SpringSecEx/StudentController.java` this code snippet is used to generate a CSRF token and send it to the client:

```java
@GetMapping("/csrf-token")
    public CsrfToken getCsrfToken(HttpServletRequest request) {
        return (CsrfToken) request.getAttribute("_csrf");

}
```

You can use the token value in POST requests to ensure that the request is legitimate and not a CSRF attack. For example, when making a POST request to the server.
Using Postman, you can use the X-CSRF-TOKEN key in the header and set its value to the token you received from the GET request to `/csrf-token`. This way, the server can verify that the request is coming from a trusted source and not from a malicious third-party site.

What if you simply generate a new session every time? Then you don't have to worry about the csrf. You can disable it you can disable the checking of the CSRF token on the server side.

### Override default Spring Security settings

The Spring Security filter chain comes before the DispatcherServlet in the request processing order. This means that the security filters will be applied to incoming requests before they reach the DispatcherServlet, which is responsible for handling the request and returning a response.
By default Spring Security provides you a filter chain. How do we change how the Security Chain Filter works? 

#### Config Class

You define Beans within the config class which will then inject the object. I created a config package.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        return http.build();
    }
```

The `@Configuration` annotation indicates that this class contains bean definitions for the application context. If I do not want the default Spring Security configuration the `@EnableWebSecurity` annotation is used to enable Spring Security's web security support and provide a custom configuration.

If I want to return a Bean for the SecurityFilterChain, I can define a method that returns a SecurityFilterChain object and annotate it with `@Bean`. This method will be called by Spring to create and configure the security filter chain for the application.
The build() method is called to create the SecurityFilterChain object based on the configurations defined in the method.

If I comment out the @EnableWebSecurity and @Bean annotations, then visit http://localhost:8080 I will see the default Spring Security login page. This is because without the custom configuration, Spring Security applies its default settings, which include requiring authentication for all requests and providing a default login page.

If I then uncomment the @EnableWebSecurity and @Bean annotations, I will see the home page, because security is not implemented. We are bypassing the default security configuration by defining our own SecurityFilterChain bean. So how do we implement security? I want the login form, but first I want
to disable CSRF protection.

This code:

```java
http.csrf(customizer -> customizer.disable());
```

...disables CSRF protection in the application. This means that the application will not require a CSRF token for POST, PUT, DELETE, or PATCH requests, which can make the application vulnerable to CSRF attacks. However, it can be useful in certain scenarios, such as when building a stateless API that does not use cookies for authentication.

But I can still access the home page without logging in. To require authentication for all requests, I can add the following code:

```java
http.authorizeHttpRequests(request -> request.anyRequest().authenticated());
```

This code configures Spring Security to require authentication for all incoming requests. The `authorizeHttpRequests` method is used to specify the authorization rules, and the `anyRequest().authenticated()` configuration means that any request to the application must be authenticated (i.e., the user must be logged in) in order to access it. If a user tries to access any page without being authenticated, a 403 Forbidden error will be returned even if you
specify the username and password in the Basic Authentication header. How do we make use of the username and password in the Basic Authentication header? We can add the following code:

```java
http.formLogin(Customizer.withDefaults());
```

This code enables form-based authentication in the application. When a user tries to access a protected resource without being authenticated, they will be redirected to a login page where they can enter their username and password. The `Customizer.withDefaults()` method applies the default configuration for form login, which includes using the default login page provided by Spring Security.

From Postman you will see the HTML code for the login page. 

This code:

```java
http.httpBasic(Customizer.withDefaults());
```

...enables HTTP Basic authentication in the application. With this configuration, clients can authenticate by including an `Authorization` header with their username and password encoded in Base64. When a user tries to access a protected resource without being authenticated, they will receive a 401 Unauthorized response, prompting them to provide their credentials. This is particularly useful for APIs or when you want to allow programmatic access to your application without using a form-based login.

We have disabled CSRF protection. What if we want our session to be stateless? We can add the following code:

```java
http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

At this point whenever I visit http://localhost:8080 I am repeatedly challenged to enter my user name and password. This is because the application is now stateless, meaning that it does not maintain any session information between requests. Each request is treated as independent, and the server does not store any information about the client's state. As a result, every time you make a request to the server, you will be prompted to provide your credentials again since there is no session to remember your authentication status. This is a common configuration for RESTful APIs where statelessness is a key principle.

I am able to login via Postman using the Basic Authentication header, but I am not able to login via the form-based login. This is because the form-based login relies on sessions to maintain the user's authentication state, and since we have configured the application to be stateless, it does not work with form-based authentication. The form-based login will not be able to create a session for the user, and therefore, it will not be able to authenticate the user successfully. In contrast, HTTP Basic authentication does not rely on sessions and can work in a stateless configuration, which is why it still works in this case.

If I disable the form-based login with stateless session management, then when I visit http://localhost:8080 a pop-up will appear asking for my username and password. This is because the application is now configured to use HTTP Basic authentication, which prompts the user for credentials through a browser dialog when accessing protected resources. Since we have disabled form-based login and set the session management to stateless, the application relies solely on HTTP Basic authentication, resulting in the pop-up prompt for credentials when trying to access the home page.

Everytime I refresh the page a new session ID is generated. This is because the application is configured to be stateless, meaning that it does not maintain any session information between requests. Each time you refresh the page, a new request is made to the server, and since there is no session to remember your authentication state, the server generates a new session ID for each request. This behavior is expected in a stateless configuration, as the server treats each request as independent and does not store any information about the client's state.

Instead of using the lambda expressions:

```java
http.csrf(customizer -> customizer.disable());
```

You can also use method chaining to achieve the same result:

```java
Customizer<CsrfConfigurer<HttpSecurity>> custCsrf = new Customizer<CsrdConfigurer<HttpSecurity>>() {
    @Override
    public void customize(CsrfConfigurer<HttpSecurity> customizer) {
        customizer.disable();
    }
};

http.csrf(custCsrf);
```

The Customizer interface is a functional interface. That type of interface has only one abstract method, which can be implemented using a lambda expression. The lambda expression provides a more concise and readable way to implement the Customizer interface without needing to create an anonymous inner class. In the original code, the lambda expression is used to disable CSRF protection in a more straightforward manner, while the method chaining approach requires more boilerplate code to achieve the same result. The lambda expression is generally preferred for its simplicity and clarity when working with functional interfaces like Customizer.

The @Override annotation is used to indicate that the method is intended to override a method in a superclass or implement a method from an interface. In this case, the customize method is overriden from the Customizer interface. The @Override annotation is not strictly necessary, but it helps to catch errors at compile time. If you accidentally misspell the method name or use the wrong parameters, the compiler will generate an error because it will not find a matching method to override. This can help prevent bugs and improve code readability by making it clear that the method is intended to override a method from a superclass or interface.

### UserDetailsService

The UserDetailsService verifies the username and password. Instead of verifying via hardcoded values in the application.properties file, we can create a Bean which will be in the Spring container and the Spring Security will pick it up.

You cannot just write `return new UserDetailsService();` because UserDetailsService is an interface and cannot be instantiated. The InMemoryUserDetailsManager implements UserDetailsManager, which extends UserDetailsService. But you cannot just write `return new InMemoryUserDetailsManager();` because if you provide a username and password, then how will it verify them? You would get a 401 Unauthorized response because we are using our own UserDetailsService and it does not have any user details configured. To fix this, we need to create user details and pass them to the InMemoryUserDetailsManager.

The InMemoryUserDetailsManager has its own `UserDetails... users` constructor that takes a variable number of UserDetails objects. The UserDetails is an interface. We need to use the User class since it implements the UserDetails interface. The User class has a builder method that means we can use dot notation to create a UserDetails object. The `withDefaultPasswordEncoder()` method is used to create a password encoder that uses a default encoding mechanism, which is not recommended for production use but is suitable for testing purposes. The `username()`, `password()`, and `roles()` methods are used to set the username, password, and roles for the user, respectively. Finally, the `build()` method is called to create the UserDetails object. 

```java
@Bean
public UserDetailsService userDetailsService() {

        UserDetails user1 = User
        .withDefaultPasswordEncoder()
        .username("shreya")
        .password("shreya123")
        .roles("USER")
        .build();

        UserDetails user2 = User
        .withDefaultPasswordEncoder()
        .username("geeta")
        .password("g33t8")
        .roles("ADMIN")
        .build();

        return new InMemoryUserDetailsManager(user1, user2);
        }
``` 

The `build()` method is called to create UserDetails objects for each user.The InMemoryUserDetailsManager constructor supports VARARGS, which means it can take a variable number of UserDetails objects as arguments. By passing user1 and user2 to the constructor, we are creating an instance of InMemoryUserDetailsManager that contains both users. This allows Spring Security to authenticate requests based on the credentials of either user1 or user2 when they attempt to log in.

### AuthenticationProvider



When you authenticate with these usernames an Authentication Object goes to something called the Authentication Provider. I do not want to use the default Authentication Provider. I want to customize it. The provider can be used to connect to a database or an LDAP server. For the database, we have a different Authentication Provider called DaoAuthenticationProvider which is a class that extends AbstractUserDetailsAuthenticationProvider, which in turn implements the AuthenticationProvider interface.

```java
@Autowired
private MyUserDetailsService myUserDetailsService;

@Bean
public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(myUserDetailsService);
        provider.setPasswordEncoder(NoOpPasswordEncoder.getInstance());  // We are not encoding the password for simplicity, but in production, use a proper password encoder.

        return provider;
}
```

I want to create my own UserDetailsService and use it in the AuthenticationProvider. I can do this by autowiring the UserDetailsService into the SecurityConfig class and then setting it in the DaoAuthenticationProvider. The `setPasswordEncoder` method is used to specify the password encoder that should be used to encode and verify passwords.

### MyUserDetailsService

```java
@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo userRepo;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userRepo.findByUsername(username);

        if(user == null) {
            System.out.println("User not found: " + username);
            throw new UsernameNotFoundException("User not found: " + username);
        }else {
            System.out.println("User found: " + username);
            //return new UserDetails();
            return new UserPrincipal(user);
        }



    }
}
```

Since our class implements the UserDetailsService interface, we need to implement the `loadUserByUsername` method. This method is responsible for loading the user's details based on their username. We use the UserRepo to find the user in the database by their username. If the user is not found, we throw a UsernameNotFoundException. If the user is found, we return a new UserDetails object that contains the user's information.

We cannot use `return new UserDetails();` because UserDetails is an interface and cannot be instantiated. Instead, we need to create a class that implements the UserDetails interface and return an instance of that class with the user's details.

I create a model called UserPrincipal that implements the UserDetails interface. This class will take a Users object in its constructor and extract the necessary information to implement the methods of the UserDetails interface. Then, in the `loadUserByUsername` method, we can return an instance of UserPrincipal with the user's details.

### UserRepo

```java
@Repository
public interface UserRepo extends JpaRepository<Users, Integer> {

    Users findByUsername(String username);
}
```

The first parameter, Users, matches the name of the table in the database.

I created a Users model class that represents the user entity in the database. The UserRepo interface extends JpaRepository, which provides CRUD operations for the Users entity. The findByUserName method is a custom query method that allows us to find a user by their username. This method will be used in the MyUserDetailsService to load the user's details based on their username during authentication.

The `findByUsername` method must be spelled properly. Property Expression Mapping is a Spring JPA feature that requires the method name to match the fields in the model class.

When Spring Data parses findByUserName, it strips away the findBy prefix, looks at the remainder (UserName), and attempts to match it against a private field inside your Users.java entity model.

Since the field in the model is `private String username;`, the method should be named `findByUsername` to match the field name. If you name it `findByUserName`, Spring Data will not be able to find a matching field and will throw an error.

### UserPrincipal

This model class refers to the user that is trying to log in. After implementing the required methods of the UserDetails interface, we can return an instance of UserPrincipal in the `loadUserByUsername` method of MyUserDetailsService, which will contain the user's details needed for authentication and authorization in Spring Security.

```java
public class UserPrincipal implements UserDetails {

    private Users user;

    public UserPrincipal(Users user) {
        this.user = user;
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority("USER"));
    }

    @Override
    public @Nullable String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

I created a constructor that takes a Users object and initializes the user field. The getAuthorities method returns a collection of GrantedAuthority objects that represent the roles or permissions assigned to the user. In this example, we are assigning a single role "USER" to all users. The getPassword and getUsername methods return the user's password and username, respectively, by accessing the corresponding fields from the Users object.


For the methods that return boolean I replaced what is returned. For example, I changed isEnabled to `return true` instead of `return UserDetails.super.isEnabled();` because I want to enable the user by default. The other methods can be implemented based on the requirements of your application, such as returning the user's password, username, and authorities (roles).

### Summary of what we did to authenticate user credentials:

1. We added the JPA and PostgreSQL dependencies to the Maven pom.xml file to enable database connectivity and JPA functionality in our Spring Boot application.
2. To the application.properties file, we added the URL, username and password for the PostgreSQL database.
3. We then configured the Security Config class. By default it was using an AuthenticationProvider but we wanted to create a DaoAuthenticationProvider. To make that work we pass a password encoder and our own UserDetailsService.
4. Since we are using our own UserDetailsService, we created a MyUserDetailsService class that implements the UserDetailsService interface. In this class, we implemented the loadUserByUsername method to load user details from the database using the UserRepo.
5. The MyUserDetailsService can't just return a UserDetails object because UserDetails is an interface. So we created a UserPrincipal class that implements the UserDetails interface and takes a Users object in its constructor to extract the necessary information for authentication.

##  Spring Security Bcrypt Password Encoder

The problem we have is that the passwords are stored in plain text in the database. This is a security risk because if the database is compromised, the attackers will have access to all the user passwords.

We can take the plaintext password, encrypt it then store ciphertext in the database. When the user tries to log in we can compare the password he enters then compare it with the decrypted ciphertext stored in the database. This way even if the database is compromised, the attackers will not have access to the plaintext passwords.

One problem is that Encryption uses a key to encrypt and decrypt the data. If the key is compromised, the attackers can decrypt the data. 

Instead of ciphertext you can create a hash. Hashing is one way so if you get a plain text you run some algorithm on it let's say SHA or MD5 and then you end up with a hash. A hash is like a fingerprint for some text.

If the text changes then the hash will change. If you have the same text then you will get the same hash. The advantage of Hash over Ciphertext is that it is not reversible. You cannot get the original text from the hash. This is why it is more secure than encryption.

`plaintext -SHA256-> hash1 -SHA256-> hash2`

The above shows the repeated hashing of the plaintext. This is called key stretching. It makes it more difficult for attackers to crack the hash using brute force attacks.

To achieve this I use BCryptPasswordEncoder which is a password encoder that uses the BCrypt hashing function to hash passwords. It also incorporates a salt to protect against rainbow table attacks and is designed to be computationally expensive to make brute-force attacks more difficult.

Everytime a user creates an account or when the app validates the password during login, we can use the BCryptPasswordEncoder to hash the password before storing it in the database or comparing it with the stored hash. This way, even if the database is compromised, the attackers will not have access to the plaintext passwords, and it will be computationally infeasible for them to reverse-engineer the original passwords from the hashes.

I create a Controller for the user creation.

```java
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Users register(@RequestBody Users user) {
        // Logic to save the user to the database
        return userService.register(user);
    }
}
```

The UserController class calls the register method of the UserService to save the user to the database and returns the saved user object.

I created a Service for the user creation.

```java
public class UserService {

    @Autowired
    private UserRepo userRepo;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    public Users register(Users user) {
        // Logic to save the user to the database
        user.setPassword(encoder.encode(user.getPassword()));
        return userRepo.save(user);
    }
}
```

The UserService class has a register method that takes a Users object as input. Before saving the user to the database, it uses the BCryptPasswordEncoder to hash the user's password by calling the `encode` method on the plaintext password. This ensures that the password is stored securely in the database as a hash rather than in plaintext.

```java
@Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(myUserDetailsService);
//        provider.setPasswordEncoder(NoOpPasswordEncoder.getInstance());  // We are not encoding the password for simplicity, but in production, use a proper password encoder.
        provider.setPasswordEncoder(new BCryptPasswordEncoder(12));

        return provider;
    }
```


I updated the SecurityConfig class to use BCryptPasswordEncoder in the authentication provider. This ensures that when a user tries to log in, the password they enter will be hashed using BCrypt and compared with the hashed password stored in the database. If the hashes match, the authentication will be successful. This adds an extra layer of security to our application by ensuring that passwords are not stored in plaintext and are protected against brute-force attacks.

I used https://www.browserling.com/tools/bcrypt to hash the passwords of users that I had already created in the database before implementing the BCryptPasswordEncoder. I then ran `update users set password = <browserling_hash>
where username = <legacy_user>;` to update the existing users' passwords in the database with their corresponding BCrypt hashes. This way, when those users try to log in, the authentication process will work correctly with the new password encoding mechanism.

## JWT and why do we need it?

Between a client and a server, the first request is for log in. You can access the server's resources after providing the correct username and password. The server will then create a session for the user and store the session ID in a cookie. The client will include this cookie in subsequent requests to access protected resources on the server. This is how traditional session-based authentication works.

You also have SSO where you login to a third-party service (like Google or Facebook) and then you can use that service to log in to other applications. This is done using OAuth2, which is an authorization framework that allows third-party applications to access a user's resources without sharing their credentials.

There is a drawback to session-based authentication and SSO. The server needs to maintain the session state for each user, which can be resource-intensive and does not scale well for large applications. Additionally, in a distributed system with multiple servers, managing sessions can become complex.

If we use the analogy of a coffee shop, I go to this shop in Hounslow. I pay Bob who works there £50 so that I can get my coffee quickly without queuing and that £50 means I am paying less per cup of coffee. The next day I go to the same shop but Bob is not there. Alice does not recognise my face, nor does she know that I paid £50 to Bob. I miss out on my coffee discount. To overcome this I am given a card with the number 102 on it that I can present to any person working there. A book is used to record a number 102 next to my name. This way, Alice can see that I paid £50 and give me the discount. This is how session-based authentication works. The server maintains a session for each user and uses a session ID to identify the user.

Suppose there is a sister shop in Ealing. I can present the same card with the number 102 on it. However, they do not have the same book that the Hounslow shop has. They do not know that I paid £50 and they will not give me the discount. The company can store the information on a shared database instead of keeping a separate copy of it at different shops. This way, both shops can access the same information and give me the discount. This is how SSO works. The third-party service (like Google or Facebook) acts as a central authority that manages the user's authentication and provides access tokens to other applications.

The disadvantage of both session-based authentication and SSO is that they require the server to maintain state for each user, which can be resource-intensive and does not scale well for large applications. Additionally, in a distributed system with multiple servers, managing sessions can become complex.

Now if we go back to the time I first went to Hounslow and met Bob, instead of giving me a card with a number on it, Bob gives me a special card with my name, the issue date and an expiry date. One day I let my mate Horace borrow this special card. Horace is cunning and he duplicates it. He goes to the shop and tries to use it. The shop owner sees that the card is valid and gives him the discount. This is a security risk because anyone can duplicate the card and use it to get the discount. What if the manager stamps the special card? Any card
without the stamp is not valid. This card is analogous to a Token. How do we represent this token in the data format? We can use XML but it is too bulky and the encoded XML is also bulky.

The alternative is JSON which even encoded is lightweight. The data on the special card is called a claim.
I'm claiming my name is Amit Kapila, my issue date is 2024-06-01 and my expiry date is 2024-12-31. This claim is represented in JSON format as follows:

```json
{
  "name": "Amit Kapila",
  "issueDate": "2024-06-01",
  "expiryDate": "2024-12-31"
}
```

I can transfer the claim between client and server.

On jwt.io you can see the data. There is a header, a payload and a signature.

Here is an example header:

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

HS256 is the algorithm used to sign the token. RS256 is RSA. ES384 is ECDSA. RSA & ECDSA are asymmetric algorithms that use a public key and a private key. HS256 is a symmetric algorithm that uses a secret key.
The typ is the type of the token which is JWT.
JWT stands for JSON Web Token.

This is the payload. The iat is the issued at time. The sub is the subject of the token. The name is the name of the user. The admin is a custom claim that indicates whether the user is an admin or not.

```json
{
  "sub": "1234567890",
  "name": "John Doe",
  "admin": true,
  "iat": 1516239022
}
```

Remember the analogy of Bob giving a stamp. Well that is the signature. The signature is created by taking the encoded header, the encoded payload, a secret key and the algorithm specified in the header and signing them together. The signature is used to verify that the token has not been tampered with and that it was issued by a trusted source.

This is an example:

```
a-string-secret-at-least-256-bits-long
```

The above string is a secret key that is at least 256 bits long. It is used to sign the JWT token. The longer and more complex the secret key, the more secure the token will be against brute-force attacks. It is important to keep the secret key safe and not expose it in client-side code or public repositories, as anyone with access to the secret key can generate valid tokens and potentially gain unauthorized access to protected resources.

Now you don't send the decoded JWT token to the client. You send the encoded JWT token which is a string that looks like this:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.KMUFsIDTnFmyG3nMiGM6H9FNFUROf3wh7SmqJp-QV30
```

Going back to the client server analogy. You do not have to maintain a session. Suppose you want to send a GET request to http://localhost:8080/students.
You also send the token. How do you send the token? The first time that you log in, the server will generate a JWT token and send it back to the client. The client will then include this token in the Authorization header of subsequent requests to access protected resources on the server. The server will verify the token and grant access if it is valid. This way, the server does not need to maintain any session state for the user, and the authentication information is contained within the token itself.

When you talk about the signature, you can use different algorithms. We have cryptographic algorithms that are used to sign the token. The most common ones are HMAC SHA256 (HS256), RSA (RS256) and ECDSA (ES384). The choice of algorithm depends on the security requirements of your application and the level of trust you have in the parties involved in token generation and verification. HS256 is a symmetric algorithm that uses a shared secret key for both signing and verification, while RS256 and ES384 are asymmetric algorithms that use a pair of public and private keys, providing stronger security but requiring more complex key management.

By doing 'JOT' you aren't achieving secrecy. You are achieving accountability. The server can verify that the token was issued by a trusted source and has not been tampered with. However, anyone who has access to the token can read its contents, so it is important to avoid including sensitive information in the token's payload. If you need to include sensitive information, consider encrypting the token or using a secure transmission method like HTTPS  to protect it from unauthorized access.

## Spring Security Project Setup for JWT

Generating the token requires a lot of steps. The same for validating the token. But it is a one time setup. Once you have it, you can use it for all your projects. You can create a library that contains the code for generating and validating JWT tokens and then include that library in your projects.

The classes for generating and validating are not in Spring Security by default. You need to have different layers.

Remember in our SecurityConfig.java we are asking our AuthenticationProvider to talk to the database to verify the username and password. If we want to use JWT then we need to add 1 more layer. The object of authentication goes to the server and there is something called Authentication Manager. The Authentication Manager is responsible for managing the authentication process. It takes the authentication request and passes it to the appropriate Authentication Provider. The Authentication Provider then verifies the credentials and returns an Authentication object if the authentication is successful.

After adding the JJWT dependency to the pom.xml file, we need to make sure that the Authentication Manager is something that I am handling. When I want to handle it I need to create a Bean for it.

```java
 @Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

In the same way we used DaoAuthenticationProvider to return an AuthenticationProvider, how can we return an AuthenticationManager? We can't just write `return new AuthenticationManager();` because AuthenticationManager is an interface and cannot be instantiated. The AuthenticationManager is typically provided by Spring Security's configuration and is not something you would create directly. Instead, you can obtain the AuthenticationManager from the AuthenticationConfiguration, which is a class that provides access to the configured AuthenticationManager.

Now that we have the AuthenticationManager, it will talk to the AuthenticationProvider to verify the username and password. If the authentication is successful, we can generate a JWT token and send it back to the client. The client will then include this token in the Authorization header of subsequent requests to access protected resources on the server. The server will verify the token and grant access if it is valid.

We are able to register the user. If you look at the UserController.java there is a POST mapping for /register. 

I want to make sure that for /login and /register, I do not need to be authenticated. For any other request, I need to be authenticated. To achieve this, I can update the securityFilterChain method in the SecurityConfig class as follows:

```java
return http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(request -> request
        .requestMatchers("/register", "/login")
        .permitAll()
        .anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .build();
``` 

We can use Postman to send a POST request to http://localhost:8080/register with the user's details in the body of the request. The UserService will then hash the password using BCryptPasswordEncoder and save the user to the database. That is registering. But what about log in?

I created a POST mapping for /login.

```java
@PostMapping("/login")
public String login(@RequestBody Users user) {
    return userService.verify(user);
//        System.out.println(user.getUsername() + " " + user.getPassword());
//        return "Login successful";
}
```

Before this POST Mapping log in was happening automatically because of the AuthenticationManager. Since we created a Bean for the AuthenticationManager, we need to handle the log in ourselves. In the UserService.java I autowire the AuthenticationManager and create a method called verify that takes a Users object as input.

```java
@Autowired
AuthenticationManager authManager;

@Autowired
private JWTService jwtService;
            ............
public String verify(Users user) {

        Authentication authentication =
        authManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));

        if(authentication.isAuthenticated()) {
        return jwtService.generateToken(user.getUsername());
        } else {
        return "Login failed";
        }
        }
```

Using the authManager, we can authenticate the user. How will we do that? In the SecurityConfig.java we have the AuthenticationProvider Bean that we can use to verify the user against the database. The authenticate method of the AuthenticationManager takes an Authentication object as input.

The UsernamePasswordAuthenticationToken is a class that implements the Authentication interface and is used to represent an authentication request with a username and password. We create a new instance of UsernamePasswordAuthenticationToken with the user's username and password, and then pass it to the authenticate method of the AuthenticationManager.

```java
@Service
public class JWTService {
    public String generateToken(String username) {
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6InJoYWVnb24iLCJpYXQiOjE1MTYyMzkwMjIsImV4cCI6MTUxNjIzOTAyMn0.udU_LBuvkiiX5V5AaukNkmLYORw-9Nd6NuG2bT9PGSY";
    }
}
```

The authentication.isAuthenticated() method checks if the authentication was successful. If it returns true, we can generate a JWT token using the JWTService and return it to the client. If it returns false, we can return a message indicating that the login failed.

One little problem at this stage. This is not a valid JWT token. It is just a string. We need to implement the logic to generate a valid JWT token using the JJWT library. This involves creating a JWT builder, setting the claims (such as the username, issue date, and expiry date), signing the token with a secret key, and then returning the generated token as a string. Once we have a valid JWT token, we can return it to the client upon successful authentication.

## Generating JWT Token

The token should have the subject, the issue date and the expiry date. The subject is the username of the user. The issue date is the date when the token was generated. The expiry date is the date when the token will expire.

```java
@Service
public class JWTService {

    private String secretKey = "mySecretKey";

    public String generateToken(String username) {

        Map<String, Objects> claims = new HashMap<>();

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
                .and()
                .signWith(getKey())
                .compact();


    }

    private Key getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

We create a Map to hold the claims that we want to include in the token. The value is Object because the claims can be of any type (String, Date, etc.). We can add the subject, issue date and expiry date to the claims map.

The `Jwts` class has a `builder()` method that returns a `JwtBuilder` object. We can use this builder to set the claims, subject, issue date, expiry date and sign the token. The `compact()` method is called to generate the final JWT token as a string.

### How do we sign the token?

In relation to the `.signWith(getKey())` we have to generate a key here. The `getKey()` method decodes the secret key from Base64 and then uses it to create a signing key for the HMAC SHA algorithm. The `Keys.hmacShaKeyFor` method is a utility method provided by the JJWT library that takes the decoded key bytes and returns a `Key` object that can be used for signing the JWT token. This ensures that the token is securely signed and can be verified by the server when it receives the token in subsequent requests.

However, in Postman, when I send a POST request to http://localhost:8080/login with the user's credentials, I am getting a 401 Unauthorized response.

The error states `io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 64 bits which is not secure enough for any JWT HMAC-SHA algorithm.  The JWT JWA Specification (RFC 7518, Section 3.2) states that keys used with HMAC-SHA algorithms MUST have a size >= 256 bits (the key size must be greater than or equal to the hash output size).  Consider using the Jwts.SIG.HS256.key() builder (or HS384.key() or HS512.key()) to create a key guaranteed to be secure enough for your preferred HMAC-SHA algorithm.  See https://tools.ietf.org/html/rfc7518#section-3.2 for more information.`

The error is occurring because the secret key used for signing the JWT token is not long enough to meet the security requirements of the HMAC-SHA algorithm. The JWT JWA Specification requires that keys used with HMAC-SHA algorithms must be at least 256 bits long (which is 32 bytes).

I changed the code:

```java
private String secretKey = "";
    
    public JWTService() {
        // Generate a random secret key
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            SecretKey sk = keyGen.generateKey();
            secretKey = Base64.getEncoder().encodeToString(sk.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating secret key", e);
        }
    }
```

In the constructor of the JWTService class, I use the javax.crypto.KeyGenerator. The `getInstance("HmacSHA256")` method creates a KeyGenerator for the HMAC-SHA256 algorithm. The `generateKey()` method returns an object of type SecretKey, which contains the generated key. From the sk instance of SecretKey, we have to get the string representation of the key. Remember in the `getKey()` method we are decoding the secret key from Base64, so we need to encode the generated key to Base64 format before storing it in the secretKey variable. The `Base64.getEncoder().encodeToString(sk.getEncoded())` method encodes the raw bytes of the generated key into a Base64 string, which can then be used as the secret key for signing JWT tokens. This ensures that the generated key meets the security requirements for HMAC-SHA algorithms and can be used to securely sign JWT tokens.

Now when I send a POST request to http://localhost:8080/login with the user's credentials, I should receive a valid JWT token in the response.

I went to jwt.io then entered  token returned in the response `eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyaGFlZ29uIiwiaWF0IjoxNzgwNzc0NDM3LCJleHAiOjE3ODA4MTA0Mzd9.6JwxQvVEkeQWTquJ0Wm9vVJ_-WiKKknzQFwO40GGgjE`. The decoded payload is as follows:

```json
{
  "sub": "rhaegon",
  "iat": 1780774437,
  "exp": 1780810437
}
```

When I send a GET request to http://localhost:8080/students and include `eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyaGFlZ29uIiwiaWF0IjoxNzgwNzc0NDM3LCJleHAiOjE3ODA4MTA0Mzd9.6JwxQvVEkeQWTquJ0Wm9vVJ_-WiKKknzQFwO40GGgjE` in the Authorization header as a Bearer token, I see a 401 Unauthorized response. This is because we have not yet implemented the logic to validate the JWT token in our application. We need to create a filter that will intercept incoming requests, extract the token from the Authorization header, validate it, and set the authentication context if the token is valid. Once we implement this filter and configure it in our security configuration, we should be able to access protected resources using the JWT token for authentication.

## Validating JWT Token

Apart from /register and /login all other endpoints require authentication. By default, the authentication filter is UsernamePasswordAuthenticationFilter. I don't want UPAF to handle the authentication first. I want UPAF to be the second filter. The first filter should be JWT Filter, which will check the token in the Authorization header. If the token is valid, it will send the request to UPAF. Before passing it to UPAF it has already verified the user has already logged in. If the token is not valid, it will return a 401 Unauthorized response.

We have to add a filter before UPAF.

```java
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/register", "/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
```

The `addFilterBefore` method is used to add the JWT filter before the UsernamePasswordAuthenticationFilter in the filter chain. This ensures that the JWT filter will be executed before the authentication process handled by the UsernamePasswordAuthenticationFilter, allowing it to validate the JWT token and set the authentication context if the token is valid.

```java
@Component
public class JwtFilter extends OncePerRequestFilter {
    @Autowired
    private JWTService jwtService;

    @Autowired
    ApplicationContext context;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyaGFlZ29uIiwiaWF0IjoxNzgwNzc0NDM3LCJleHAiOjE3ODA4MTA0Mzd9.6JwxQvVEkeQWTquJ0Wm9vVJ_-WiKKknzQFwO40GGgjE
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        if(authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            username = jwtService.extractUsername(token);
        }

        if(username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Validate the token and set the authentication in the security context
            UserDetails userDetails = context.getBean(MyUserDetailsService.class).loadUserByUsername(username); // Load user details using the username

            if(jwtService.validateToken(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);

    }
}
```

The JwtFilter class extends OncePerRequestFilter, which ensures that the filter is executed only once per request. The `doFilterInternal` method is where we will implement the logic to extract the JWT token from the Authorization header, validate it, and set the authentication context if the token is valid.

Behind the scenes everything in Spring Web is HTTP Servlets which give you a request object and a response object. The request contains `Bearer <token>` in the Authorization header. 

```java
public class JWTService {

    private String secretKey = "";

    public JWTService() {
        // Generate a random secret key
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            SecretKey sk = keyGen.generateKey();
            secretKey = Base64.getEncoder().encodeToString(sk.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating secret key", e);
        }
    }
    public String generateToken(String username) {

        Map<String, Objects> claims = new HashMap<>();

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
                .and()
                .signWith(getKey())
                .compact();


    }

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}
```

The first thing we are doing is generating a token. Who is responsible for generating the token? Within the SecurityConfig.java we have a hold on the AuthenticationManager. Then within the UserController.java we have a login
form. The login form calls the verify method in the UserService.java which uses the AuthenticationManager to authenticate the user. We are using the UsernamePasswordAuthenticationToken to authenticate the user. If the authentication is successful, we can generate a JWT token. The JWTService has a generateToken method that takes the username as input and generates a JWT token with the username as the subject, the issue date, and the expiry date. In the next request we are not sending a username and password. In that scenario do not go to the UsernamePasswordAuthenticationFilter. Instead, go to the jwtFilter. The jwtFilter will extract the token from the Authorization header, validate it, and if it is valid. Then get the username, the token, from the database get the data based upon the username you are passing and verify if it matches. If it matches then you have to create a new
authentication object and have to set that in the context. Once it is done you are through because for the next filter you have specified that I have the authentication but there is one problem.

In the validate token we have to actually validate token and to do that you have to extract the username. To do so, we have to extract the claims from the token. The claims are the data that we have put in the token when we generated it. We can use the Jwts.parser() to parse the token and extract the claims. We also need to verify the token using the same secret key that we used to sign the token. If the token is valid, we can extract the username from the claims and compare it with the username from the UserDetails object. If they match and the token is not expired, then we can consider the token as valid.

## Spring Security Google and GitHub Login

