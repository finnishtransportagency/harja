(ns harja-laadunseuranta.tiedot.indexeddb
  (:require [cljs.core.async :as async :refer [<! >! chan close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja-laadunseuranta.tiedot.indexeddb-macros :refer [with-transaction with-objectstore with-cursor]]))

(defn- indexed-db []
  (or (.-indexedDB js/window)
      (.-mozIndexedDB js/window)
      (.-webkitIndexedDB js/window)
      (.-msIndexedDB js/window)))

(defn- transaction []
  (or (.-IDBTransaction js/window)
      (.-webkitIDBTransaction js/window)
      (.-msIDBTransaction js/window)))

(defn- open-indexed-db [db-name version on-error on-success initialize]
  (let [db (.open (indexed-db) db-name version)]
    (set! (.-onerror db) #(on-error (-> % .-target .-errorCode)))
    (set! (.-onsuccess db) #(on-success (-> % .-target .-result)))
    (set! (.-onupgradeneeded db) #(initialize (-> % .-target .-result)))))

(defn- create-objectstore [db store-name keypath auto-increment create-indices on-complete]
  (let [os (.createObjectStore db store-name #js {:keyPath (name keypath)
                                                  :autoIncrement auto-increment})
        transaction (.-transaction os)]
    (create-indices os)
    (set! (.-oncomplete transaction) on-complete)))

(defn- create-index [os nimi key unique]
  (.createIndex os nimi (name key) #js {:unique unique}))

(defn add-object [store object]
  (.add store (clj->js object)))

(defn delete-object [store key]
  (.delete store key))

(defn get-object [store key]
  (.get store key))

(defn put-object [store object]
  (.put store (clj->js object)))

(defn delete-at-cursor [cursor]
  (when cursor
    (let [req (.delete cursor)]
      (set! (.-onsuccess req) (fn [] (.log js/console "entry tuhottu!")))
      (set! (.-onerror req) (fn [] (.log js/console "ei voitu tuhota!"))))))

(defn cursor-continue [cursor]
  (when cursor
    (.continue cursor)))

(defn delete-indexed-db
  "Poista IndexedDB-tietokanta"
  [nimi]
  (.deleteDatabase (indexed-db) nimi))

(defn close-indexed-db
  "Sulje IndexedDB-tietokanta"
  [db]
  (.close db))

(defn- create-objectstores [db stores]
  (doseq [store stores]
    (try
      (create-objectstore db
                         (:name store)
                         (:key-path store)
                         (or (:auto-increment store) false)
                         (fn [os]
                           (doseq [idx (:indexes store)]
                             (create-index os (:name idx) (:key idx) (:unique idx))))
                         (fn [event]
                           (.log js/console "objectstoret alustettu")))
      (catch js/Error e
        (.log js/console "Object storen luomisessa virhe: " e)))))

(defn create-indexed-db
  "Luo tai avaa IndexedDB -tietokannan ja rakentaa/päivittää scheman"
  [nimi options]
  (let [channel (chan)]
    (open-indexed-db nimi (:version options)
                     #(do
                        (when-let [error-handler (:on-error options)]
                          (error-handler %))
                        (close! channel))
                     #(do
                        (when-let [success-handler (:on-success options)]
                          (success-handler))
                        (go (>! channel %)))
                     #(create-objectstores % (:objectstores options)))
    channel))
