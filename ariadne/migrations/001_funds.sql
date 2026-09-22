CREATE TABLE IF NOT EXISTS fund (
  fund_code char(6) PRIMARY KEY CHECK (fund_code ~ '^[0-9]{6}$'),
  fund_name text NOT NULL,
  fund_type text,
  pinyin_abbreviation text,
  pinyin_full_name text
);

CREATE TABLE IF NOT EXISTS fund_nav (
  fund_code char(6) NOT NULL REFERENCES fund(fund_code) ON DELETE NO ACTION ON UPDATE NO ACTION,
  nav_date date NOT NULL,
  unit_nav numeric(20,10) NOT NULL CHECK (unit_nav > 0),
  PRIMARY KEY (fund_code, nav_date)
);
