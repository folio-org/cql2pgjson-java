DELETE FROM users;
INSERT INTO users (user_data) VALUES
    ('{"name": "a", "email": "\\",       "address": {"city": "*?*",   "zip": 1}, "lang": ["en", "pl"]}'),
    ('{"name": "b", "email": "\\\\",     "address": {"city": "*?*",   "zip": 2}, "lang": ["en", "pl"]}'),
    ('{"name": "c", "email": "*",        "address": {"city": "?\\?",  "zip": 3}, "lang": ["en", "dk", "fi"]}'),
    ('{"name": "d", "email": "**",       "address": {"city": "?\\?",  "zip": 4}, "lang": ["en", "dk", "fi"]}'),
    ('{"name": "e", "email": "?",        "address": {"city": "\\*\\", "zip": 5}, "lang": ["en", "dk"]}'),
    ('{"name": "f", "email": "??",       "address": {"city": "\\*\\", "zip": 6}, "lang": ["en", "dk"]}'),
    ('{"name": "g", "email": "\\\"",     "address": {"city": "\"*\"", "zip": 7}, "lang": ["en", "dk"]}'),
    ('{"name": "h", "email": "\\\"\\\"", "address": {"city": "\"*\"", "zip": 8}, "lang": ["en", "dk"]}');
