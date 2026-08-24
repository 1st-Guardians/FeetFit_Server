UPDATE measurement_session
SET status = 'ANALYZING'
WHERE status = 'PROCESSING';
