DELETE FROM users;
INSERT INTO users (user_data) VALUES
    ('{"name": "Jo Jane", "email": "jo@example.com", "address": {"city": "Sydhavn", "zip": 2450}, "lang": ["en", "pl"]}'),
    ('{"name": "Ka Keller", "email": "ka@example.com", "address": {"city": "Fred", "zip": 1900}, "lang": ["en", "dk", "fi"]}'),
    ('{"name": "Lea Long", "email": "lea@example.com", "address": {"city": "Søvang", "zip": 2791}, "lang": ["en", "dk"]}');