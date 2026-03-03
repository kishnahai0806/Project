INSERT INTO categories(name, description)
VALUES
    ('Tech Talks', 'Engineering talks and technical workshops'),
    ('Career', 'Career fairs, networking, interview prep'),
    ('Clubs', 'Club showcases and weekly meetups'),
    ('Sports', 'Campus sports and recreation events')
ON CONFLICT (name) DO NOTHING;
