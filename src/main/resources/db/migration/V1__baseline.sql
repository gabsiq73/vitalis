--
-- PostgreSQL database dump
--

-- Dumped from database version 16.3 (Debian 16.3-1.pgdg120+1)
-- Dumped by pg_dump version 16.3 (Debian 16.3-1.pgdg120+1)




--
-- Name: tb_cash_movement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_cash_movement (
    id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    amount numeric(10,2) NOT NULL,
    direction character varying(255),
    note character varying(255),
    occurred_at timestamp(6) without time zone NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT tb_cash_movement_direction_check CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[]))),
    CONSTRAINT tb_cash_movement_type_check CHECK (((type)::text = ANY ((ARRAY['ENTRY'::character varying, 'ADJUSTMENT'::character varying, 'WITHDRAWAL'::character varying])::text[])))
);


--
-- Name: tb_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_client (
    cli_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    cli_address character varying(255),
    balance numeric(10,2),
    cli_bottles_debt integer,
    cli_status character varying(255) NOT NULL,
    cli_type character varying(255) NOT NULL,
    cli_is_active boolean,
    cli_name character varying(255) NOT NULL,
    cli_notes character varying(255),
    cli_phone character varying(255),
    CONSTRAINT tb_client_cli_status_check CHECK (((cli_status)::text = ANY ((ARRAY['PAID'::character varying, 'OVERDUE'::character varying])::text[]))),
    CONSTRAINT tb_client_cli_type_check CHECK (((cli_type)::text = ANY ((ARRAY['RETAIL'::character varying, 'RESELLER'::character varying, 'AVULSO'::character varying])::text[])))
);


--
-- Name: tb_client_credit_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_client_credit_entry (
    id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    amount numeric(10,2) NOT NULL,
    date timestamp(6) without time zone NOT NULL,
    method character varying(255) NOT NULL,
    client_cli_id uuid NOT NULL,
    source_payment_pay_id uuid,
    CONSTRAINT tb_client_credit_entry_method_check CHECK (((method)::text = ANY ((ARRAY['PIX'::character varying, 'DINHEIRO'::character varying, 'SALDO'::character varying])::text[])))
);


--
-- Name: tb_client_price; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_client_price (
    cp_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    cp_price numeric(10,2),
    cli_id uuid NOT NULL,
    prod_id uuid NOT NULL
);


--
-- Name: tb_depot_bottle_debt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_depot_bottle_debt (
    dbd_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    dbd_completion_date timestamp(6) without time zone,
    dbd_debt_date timestamp(6) without time zone NOT NULL,
    dbd_notes character varying(255),
    dbd_quantity integer NOT NULL,
    dbd_status character varying(255) NOT NULL,
    cli_id uuid NOT NULL,
    CONSTRAINT tb_depot_bottle_debt_dbd_status_check CHECK (((dbd_status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying])::text[])))
);


--
-- Name: tb_gas_settlement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_gas_settlement (
    gas_set_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    gas_sup_amount numeric(10,2) NOT NULL,
    gas_sup_settled boolean NOT NULL,
    gas_sup_settled_date timestamp(6) without time zone,
    gas_sup_settlement_type character varying(255) NOT NULL,
    gas_sup_id uuid NOT NULL,
    ord_item_id uuid,
    CONSTRAINT tb_gas_settlement_gas_sup_settlement_type_check CHECK (((gas_sup_settlement_type)::text = ANY ((ARRAY['YOU_OWE'::character varying, 'SUPPLIER_OWE'::character varying])::text[])))
);


--
-- Name: tb_gas_supplier; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_gas_supplier (
    gas_sup_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    gas_sup_name character varying(255) NOT NULL,
    gas_sup_notes character varying(255)
);


--
-- Name: tb_loaned_bottle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_loaned_bottle (
    lb_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    lb_loan_date timestamp(6) without time zone,
    lb_status character varying(255) NOT NULL,
    lb_qtd integer,
    lb_return_date timestamp(6) without time zone,
    cli_id uuid NOT NULL,
    ord_id uuid,
    prod_id uuid NOT NULL,
    CONSTRAINT tb_loaned_bottle_lb_status_check CHECK (((lb_status)::text = ANY ((ARRAY['LOANED'::character varying, 'RETURNED'::character varying])::text[])))
);


--
-- Name: tb_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_order (
    ord_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    ord_cancellation_group uuid,
    ord_delivery_date timestamp(6) without time zone,
    ord_notes character varying(2000),
    ord_payment_status character varying(255) NOT NULL,
    ord_status character varying(255) NOT NULL,
    cli_id uuid NOT NULL,
    CONSTRAINT tb_order_ord_payment_status_check CHECK (((ord_payment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PARTIAL'::character varying, 'PAID'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT tb_order_ord_status_check CHECK (((ord_status)::text = ANY ((ARRAY['PENDING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: tb_order_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_order_item (
    ord_item_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    ord_item_bottle_expiration date,
    ord_item_quantity integer NOT NULL,
    ord_item_unit_price numeric(10,2) NOT NULL,
    gas_sup_id uuid,
    ord_id uuid NOT NULL,
    prod_id uuid NOT NULL
);


--
-- Name: tb_payment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_payment (
    pay_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    pay_amount numeric(10,2) NOT NULL,
    pay_automatic_gas boolean DEFAULT false NOT NULL,
    pay_date timestamp(6) without time zone NOT NULL,
    pay_method character varying(255) NOT NULL,
    pay_notes character varying(255),
    ord_id uuid NOT NULL,
    CONSTRAINT tb_payment_pay_method_check CHECK (((pay_method)::text = ANY ((ARRAY['PIX'::character varying, 'DINHEIRO'::character varying, 'SALDO'::character varying])::text[])))
);


--
-- Name: tb_product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_product (
    prod_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    prod_base_price numeric(10,2),
    prod_last_cost_price numeric(10,2),
    prod_is_active boolean,
    prod_name character varying(255) NOT NULL,
    prod_reseller_price numeric(10,2),
    prod_type character varying(255) NOT NULL,
    gas_supplier_id uuid,
    CONSTRAINT tb_product_prod_type_check CHECK (((prod_type)::text = ANY ((ARRAY['WATER'::character varying, 'GAS'::character varying])::text[])))
);


--
-- Name: tb_stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_stock (
    stock_id uuid NOT NULL,
    create_date timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    last_modified_date timestamp(6) without time zone NOT NULL,
    modified_by character varying(255),
    stock_minimum integer,
    stock_qtd integer,
    prod_id uuid
);


--
-- Name: tb_system_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_system_config (
    id bigint NOT NULL,
    pickup_discount_cents integer NOT NULL
);


--
-- Name: tb_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tb_user (
    user_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    user_email character varying(100) NOT NULL,
    user_first_name character varying(50) NOT NULL,
    updated_by character varying(255),
    updated_at timestamp(6) without time zone NOT NULL,
    user_last_name character varying(50),
    user_password character varying(255) NOT NULL,
    user_role character varying(255) NOT NULL,
    user_username character varying(30) NOT NULL,
    CONSTRAINT tb_user_user_role_check CHECK (((user_role)::text = ANY ((ARRAY['ADMIN'::character varying, 'SELLER'::character varying])::text[])))
);


--
-- Name: tb_cash_movement tb_cash_movement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_cash_movement
    ADD CONSTRAINT tb_cash_movement_pkey PRIMARY KEY (id);


--
-- Name: tb_client_credit_entry tb_client_credit_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client_credit_entry
    ADD CONSTRAINT tb_client_credit_entry_pkey PRIMARY KEY (id);


--
-- Name: tb_client tb_client_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client
    ADD CONSTRAINT tb_client_pkey PRIMARY KEY (cli_id);


--
-- Name: tb_client_price tb_client_price_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client_price
    ADD CONSTRAINT tb_client_price_pkey PRIMARY KEY (cp_id);


--
-- Name: tb_depot_bottle_debt tb_depot_bottle_debt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_depot_bottle_debt
    ADD CONSTRAINT tb_depot_bottle_debt_pkey PRIMARY KEY (dbd_id);


--
-- Name: tb_gas_settlement tb_gas_settlement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_gas_settlement
    ADD CONSTRAINT tb_gas_settlement_pkey PRIMARY KEY (gas_set_id);


--
-- Name: tb_gas_supplier tb_gas_supplier_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_gas_supplier
    ADD CONSTRAINT tb_gas_supplier_pkey PRIMARY KEY (gas_sup_id);


--
-- Name: tb_loaned_bottle tb_loaned_bottle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_loaned_bottle
    ADD CONSTRAINT tb_loaned_bottle_pkey PRIMARY KEY (lb_id);


--
-- Name: tb_order_item tb_order_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_order_item
    ADD CONSTRAINT tb_order_item_pkey PRIMARY KEY (ord_item_id);


--
-- Name: tb_order tb_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_order
    ADD CONSTRAINT tb_order_pkey PRIMARY KEY (ord_id);


--
-- Name: tb_payment tb_payment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_payment
    ADD CONSTRAINT tb_payment_pkey PRIMARY KEY (pay_id);


--
-- Name: tb_product tb_product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_product
    ADD CONSTRAINT tb_product_pkey PRIMARY KEY (prod_id);


--
-- Name: tb_stock tb_stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_stock
    ADD CONSTRAINT tb_stock_pkey PRIMARY KEY (stock_id);


--
-- Name: tb_system_config tb_system_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_system_config
    ADD CONSTRAINT tb_system_config_pkey PRIMARY KEY (id);


--
-- Name: tb_user tb_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_user
    ADD CONSTRAINT tb_user_pkey PRIMARY KEY (user_id);


--
-- Name: tb_user ukig8f9fkp6x3vcbjski1u91wds; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_user
    ADD CONSTRAINT ukig8f9fkp6x3vcbjski1u91wds UNIQUE (user_username);


--
-- Name: tb_user ukky6rbh0axwmbamyg6wl4a7imb; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_user
    ADD CONSTRAINT ukky6rbh0axwmbamyg6wl4a7imb UNIQUE (user_email);


--
-- Name: tb_client_credit_entry ukojveqj891syunnql4051q29xy; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client_credit_entry
    ADD CONSTRAINT ukojveqj891syunnql4051q29xy UNIQUE (source_payment_pay_id);


--
-- Name: tb_gas_settlement ukrg8g1g586akpxfeljdickq93g; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_gas_settlement
    ADD CONSTRAINT ukrg8g1g586akpxfeljdickq93g UNIQUE (ord_item_id);


--
-- Name: tb_gas_settlement fk14cqoalwjsggic0kthxjw8xcs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_gas_settlement
    ADD CONSTRAINT fk14cqoalwjsggic0kthxjw8xcs FOREIGN KEY (gas_sup_id) REFERENCES public.tb_gas_supplier(gas_sup_id);


--
-- Name: tb_client_price fk6o4xlwfjuqfhhtomyhfgnynfe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client_price
    ADD CONSTRAINT fk6o4xlwfjuqfhhtomyhfgnynfe FOREIGN KEY (prod_id) REFERENCES public.tb_product(prod_id);


--
-- Name: tb_depot_bottle_debt fkaqg1uuvi4f5jw6ruxuwwnol7j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_depot_bottle_debt
    ADD CONSTRAINT fkaqg1uuvi4f5jw6ruxuwwnol7j FOREIGN KEY (cli_id) REFERENCES public.tb_client(cli_id);


--
-- Name: tb_product fket2i9u435i34p92jnhuju3qg9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_product
    ADD CONSTRAINT fket2i9u435i34p92jnhuju3qg9 FOREIGN KEY (gas_supplier_id) REFERENCES public.tb_gas_supplier(gas_sup_id);


--
-- Name: tb_client_credit_entry fkgococd5f35pddvo9ci4uqp2od; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client_credit_entry
    ADD CONSTRAINT fkgococd5f35pddvo9ci4uqp2od FOREIGN KEY (client_cli_id) REFERENCES public.tb_client(cli_id);


--
-- Name: tb_order_item fkidud0bnxprvr34gwafiykua4t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_order_item
    ADD CONSTRAINT fkidud0bnxprvr34gwafiykua4t FOREIGN KEY (gas_sup_id) REFERENCES public.tb_gas_supplier(gas_sup_id);


--
-- Name: tb_loaned_bottle fkifh6v4sj49vnlf498rxwx9jqd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_loaned_bottle
    ADD CONSTRAINT fkifh6v4sj49vnlf498rxwx9jqd FOREIGN KEY (cli_id) REFERENCES public.tb_client(cli_id);


--
-- Name: tb_loaned_bottle fkjqaey75kd7jhquisfy9gveew3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_loaned_bottle
    ADD CONSTRAINT fkjqaey75kd7jhquisfy9gveew3 FOREIGN KEY (prod_id) REFERENCES public.tb_product(prod_id);


--
-- Name: tb_client_price fkkllv72xxehuu3nbwkx8u0cxkl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client_price
    ADD CONSTRAINT fkkllv72xxehuu3nbwkx8u0cxkl FOREIGN KEY (cli_id) REFERENCES public.tb_client(cli_id);


--
-- Name: tb_loaned_bottle fkllqeqtarp9jyyh71r69k37g0f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_loaned_bottle
    ADD CONSTRAINT fkllqeqtarp9jyyh71r69k37g0f FOREIGN KEY (ord_id) REFERENCES public.tb_order(ord_id);


--
-- Name: tb_gas_settlement fkm0nhpcc5pfgskmactr9bpdbcl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_gas_settlement
    ADD CONSTRAINT fkm0nhpcc5pfgskmactr9bpdbcl FOREIGN KEY (ord_item_id) REFERENCES public.tb_order_item(ord_item_id);


--
-- Name: tb_payment fkmnu47b2ir3j54qx3iumarrmn6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_payment
    ADD CONSTRAINT fkmnu47b2ir3j54qx3iumarrmn6 FOREIGN KEY (ord_id) REFERENCES public.tb_order(ord_id);


--
-- Name: tb_order_item fkomqyq81u4shyn5rsjr5mrrn4o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_order_item
    ADD CONSTRAINT fkomqyq81u4shyn5rsjr5mrrn4o FOREIGN KEY (prod_id) REFERENCES public.tb_product(prod_id);


--
-- Name: tb_order fkoxx049eg81jjgslue50113ksu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_order
    ADD CONSTRAINT fkoxx049eg81jjgslue50113ksu FOREIGN KEY (cli_id) REFERENCES public.tb_client(cli_id);


--
-- Name: tb_client_credit_entry fkq6jq00dg91v7egjkbs6swb7uk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_client_credit_entry
    ADD CONSTRAINT fkq6jq00dg91v7egjkbs6swb7uk FOREIGN KEY (source_payment_pay_id) REFERENCES public.tb_payment(pay_id);


--
-- Name: tb_order_item fktkowd2blflhne9ton5rb66sn6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_order_item
    ADD CONSTRAINT fktkowd2blflhne9ton5rb66sn6 FOREIGN KEY (ord_id) REFERENCES public.tb_order(ord_id);


--
-- Name: tb_stock fkwfx5wjx17n9xol1tiapi34xk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tb_stock
    ADD CONSTRAINT fkwfx5wjx17n9xol1tiapi34xk FOREIGN KEY (prod_id) REFERENCES public.tb_product(prod_id);


--
-- PostgreSQL database dump complete
--

