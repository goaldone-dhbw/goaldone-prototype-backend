# Super-Admins

## Setup
Make a copy of super-admins.yml.example to super-admins.yml and add the email addresses of the users you want to have super-admin access. 
These users will be able to manage all organizations and users in the system.

Now run this command:

MacOS / Linux:
```bash
./mvnw clean generate-sources
```

Windows:
```cmd
./mvnw.cmd clean generate-sources
```

This will auto generate models and interfaces based on the openapi.yaml contract.

Then to start the application, run:
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

or start it in the IDE with the `dev` profile active.