(ns dt.core
  (:require [datomic.api :as d]
            [clojure.test :refer :all]))

(def test-db
  (-> "datomic:mem://test-db"
      (doto (d/create-database))
      (d/connect)
      (d/db)))

(deftest ident-resolution
  ;; datomic-free 0.9.5390
  ;; expected: (= (d/q (quote [:find ?e :in $ [?ident ...] [?e ...] :where [?e :db/ident ?ident]]) test-db [:db/add] [:db/add]) #{[:db/add]})
  ;;   actual: (not (= #{} #{[:db/add]}))
  (is (= (d/q '[:find ?e
                :in $ [?e ...] [?ident ...]
                :where [?e :db/ident ?ident]]
              test-db
              [:db/add]
              [:db/add])
         #{[:db/add]}))
  (is (= (d/q '[:find ?e
                :in $ [?ident ...] [?e ...]
                :where [?e :db/ident ?ident]]
              test-db
              [:db/add]
              [:db/add])
         #{[:db/add]})))

(deftest card-many-cas
  ;; datomic-free 0.9.5390
  ;; expected: (= #{:oranges} (:person/items (d/entity db pid)))
  ;; actual: (not (= #{:oranges} #{:apples :oranges}))
  (let [db
        (-> test-db
            (d/with [{:db/id (d/tempid :db.part/db)
                      :db/ident :person/items
                      :db/valueType :db.type/keyword
                      :db/cardinality :db.cardinality/many
                      :db.install/_attribute :db.part/db}])
            (:db-after))
        pid (d/tempid :db.part/user)
        {db :db-after :keys [tempids]}
        (d/with db [{:db/id pid
                     :person/items [:apples]}])
        pid (d/resolve-tempid db tempids pid)
        db (-> db
               (d/with [[:db.fn/cas pid :person/items :apples :oranges]] )
               (:db-after))]
    (is (= #{:oranges}
           (:person/items (d/entity db pid))))))

(deftest reverse-attrib-assertion
  ;; datomic-free 0.9.5390
  ;; ERROR in (reverse-attrib-assertion) (error.clj:57)
  ;; expected: (d/with db [{:db/id :apples, :person/_items #{:person-a :person-b}}])
  ;;   actual: datomic.impl.Exceptions$IllegalArgumentExceptionInfo: :db.error/not-an-entity Unable to resolve entity: #{:person-a :person-b} in datom [#{:person-a :person-b} :person/items :apples]
  (let [db
        (-> test-db
            (d/with [{:db/id (d/tempid :db.part/db)
                      :db/ident :person/items
                      :db/valueType :db.type/ref
                      :db/cardinality :db.cardinality/many
                      :db.install/_attribute :db.part/db}
                     {:db/id (d/tempid :db.part/user)
                      :db/ident :person-a}
                     {:db/id (d/tempid :db.part/user)
                      :db/ident :person-b}
                     {:db/id (d/tempid :db.part/user)
                      :db/ident :apples}])
            (:db-after))]
    (is (d/with db [{:db/id :apples
                     :person/_items #{:person-a :person-b}}]))))
