(ns harja.tyokalut.spec-apurit
  (:require [clojure.spec :as s]
            [clojure.set :as set]
    #?@(:clj [
            [clojure.future :refer :all]])))

;; PostgreSQL raja-arvot

(def postgres-int-min -2147483648)
(def postgres-int-max 2147483647)

(s/def ::postgres-int (s/and int? #(s/int-in-range? postgres-int-min postgres-int-max %)))
(s/def ::postgres-serial (s/and nat-int? #(s/int-in-range? 1 postgres-int-max %)))

;; Yleiset apufunktiot

(defn poista-nil-avaimet [mappi]
  (clojure.walk/postwalk
    (fn [elementti]
      (if (and (map? elementti)
               (not (record? elementti)))
        (let [m (into {} (remove (comp nil? second) elementti))]
          (when (seq m)
            m))
        elementti))
    mappi))

(declare namespacefy)

(defn- namespacefy-map [map-x {:keys [ns except custom inner] :as options}]
  (let [except (or except #{})
        custom (or custom {})
        inner (or inner {})
        keys-to-be-modified (filter (comp not except) (keys map-x))
        original-keyword->namespaced-keyword (apply merge (map
                                                            #(-> {% (keyword (str (name ns) "/" (name %)))})
                                                            keys-to-be-modified))
        namespacefied-inner-maps (apply merge (map
                                                #(-> {% (namespacefy (% map-x) (% inner))})
                                                (keys inner)))
        inner-keys-in-map-x (into #{} (filter #((into #{} (keys map-x)) %) (keys inner)))
        map-x-with-modified-inner-maps (merge map-x (select-keys namespacefied-inner-maps inner-keys-in-map-x))
        final-rename-logic (merge original-keyword->namespaced-keyword custom)]
    (set/rename-keys map-x-with-modified-inner-maps final-rename-logic)))

(defn namespacefy
  "Jos data on map, lisää keywordien nimien eteen namespacen.
   Jos vector, suorittaa saman operaation jokaiselle vectorin elementille erikseen.

   Optiot on map, joka määrittelee, millä logiikalla namespace lisätään:
   :ns        Avain, joka kertoo lisättävän namespacen nimen.
              Tämä namespace lisätään datan kaikille ykköstason avaimille (ks. poikkeukset alta).
   :except    Setti avaimia, joiden nimiä ei määritellä uudelleen.
   :custom    Mappi avaimia datassa. Arvoilla määritellään eri namespace kuin mitä :ns määrittelee.
   :inner     Mappi avaimia datassa, joiden arvo on niin ikään map.
              Arvolla määritellään logiikka, jolla sisemmän mapin avainten nimet muunnetaan.

  Esimerkki:
  Olkoon map:
  {:name \"player1\" :hp 1 :tasks {:id 666 :time 5} :points 7 :foobar nil}

  Sen muunnos:
  (namespacefy data {:ns :our.domain.player ;; Määrittelee oletusnamespacen kaikille avaimille
                     :except #{:foobar} ;; :foobar avaimeen ei kosketa
                     :custom {:points :our.domain.point/points} ;; :points avaimelle määritellään poikkeava namespace
                     :inner {:tasks {:ns :our.domain.task}}}) ;; :tasks sisällölle muunnoslogiikka samalla tavalla
  {:our.domain.player/name \"player1\"
   :our.domain.player/hp 1
   :our.domain.player/tasks {:our.domain.task/id 666
                             :our.domain.task/time 5}
   :our.domain.point/points 7
   :foobar nil}"
  [data options]
  (cond (map? data)
        (namespacefy-map data options)

        (vector? data)
        (mapv #(namespacefy-map % options) data)))

(declare unnamespacefy)

(defn- unnamespacefy-map
  [map-x {:keys [except recur?] :as options}]
  (let [except (or except #{})
        recur? (or recur? false)
        keys-to-be-modified (filter (comp not except) (keys map-x))
        original-keyword->unnamespaced-keyword (apply merge (map
                                                              #(-> {% (keyword (name %))})
                                                              keys-to-be-modified))
        keys-to-inner-maps (filter (fn [avain]
                                     (let [sisalto (avain map-x)]
                                       (or (map? sisalto) (vector? sisalto))))
                                   (keys map-x))
        unnamespacefied-inner-maps (apply merge (map
                                                  #(-> {% (unnamespacefy (% map-x))})
                                                  keys-to-inner-maps))
        map-x-with-modified-inner-maps (if recur?
                                         (merge map-x unnamespacefied-inner-maps)
                                         map-x)]
    (set/rename-keys map-x-with-modified-inner-maps original-keyword->unnamespaced-keyword)))

(defn unnamespacefy
  "Poistaa namespacetettujen avainten namespacet mapista tai vectorista mappeja.

  Optiot:
  except        Setti avaimia, joiden nimeen ei kosketa.
  :recur?       Jos true, poistaa myös sisäkkäisten mappien ja vectorien nimiavaruudet."
  ([data] (unnamespacefy data {}))
  ([data options]
   ;; TODO Vaarana on, että sama perusosa löytyy useammasta namespacetetusta keywordista.
   ;; Pitäisi havaita tämä ja kieltäytyä muuntamasta ennemmin kuin muuntaa datan väärin.
   (cond (map? data)
         (unnamespacefy-map data options)

         (vector? data)
         (mapv #(unnamespacefy-map % options) data))))

(defn get-un [map key]
  ;; TODO Etsi eka mätsi
  )