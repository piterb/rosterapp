create table if not exists hello (
  id uuid primary key,
  message varchar(255) not null,
  created_at timestamptz not null default now()
);
