DELETE FROM users;
INSERT INTO users (user_data) VALUES
    ('{"name": "a", "email": "\\",   "address": {"city": "*?*",   "zip":18}, "lang": []}'),
    ('{"name": "b", "email": "\\\\", "address": {"city": "*?*",   "zip":17}, "lang": []}'),
    ('{"name": "c", "email": "*",    "address": {"city": "?\\?",  "zip": 8}, "lang": []}'),
    ('{"name": "d", "email": "**",   "address": {"city": "?\\?",  "zip": 7}, "lang": []}'),
    ('{"name": "e", "email": "?",    "address": {"city": "\\*\\", "zip": 6}, "lang": []}'),
    ('{"name": "f", "email": "??",   "address": {"city": "\\?\\", "zip": 5}, "lang": []}'),
    ('{"name": "g", "email": "\"",   "address": {"city": "\"*\"", "zip": 4}, "lang": []}'),
    ('{"name": "h", "email": "\"\"", "address": {"city": "\"?\"", "zip": 3}, "lang": []}'),
    ('{"name": "i", "email": "''",   "address": {"city": "''*''", "zip": 2}, "lang": []}'),
    ('{"name": "j", "email": "''''", "address": {"city": "''?''", "zip": 1}, "lang": []}');
