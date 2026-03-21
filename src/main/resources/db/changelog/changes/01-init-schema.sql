

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       first_name VARCHAR(100),
                       last_name VARCHAR(100),
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          name VARCHAR(255) NOT NULL,
                          description TEXT,
                          price DECIMAL(19, 2) NOT NULL CHECK (price >= 0),
                          stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id UUID NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        total_amount DECIMAL(19, 2) NOT NULL CHECK (total_amount >= 0),
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_orders_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE payments (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          order_id UUID NOT NULL UNIQUE,
                          amount DECIMAL(19, 2) NOT NULL CHECK (amount >= 0),
                          provider VARCHAR(100) NOT NULL, -- e.g., Stripe, PayPal
                          provider_transaction_id VARCHAR(255) UNIQUE,
                          status VARCHAR(50) NOT NULL,
                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_payments_orders FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT
);

-- INDEXES
-- Foreign keys are not automatically indexed in Postgres; doing it manually prevents table scans during JOINs.
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_payments_order_id ON payments(order_id);

-- rollback DROP TABLE payments;
-- rollback DROP TABLE orders;
-- rollback DROP TABLE products;
-- rollback DROP TABLE users;