(ns harja.tyokalut.spec-apurit
  (:require [clojure.spec :as s]
            [clojure.set :as set]
    #?@(:clj [[clojure.future :refer :all]])))

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


(defn namespacefy
  "Lisää annetun mapin (X) keywordien nimien eteen namespacen.

   Optiot on map, joka määrittelee, millä logiikalla namespace lisätään:
   :ns        Avain, joka kertoo lisättävän namespacen nimen.
              Tämä namespace lisätään X:n kaikille ykköstason avaimille (ks. poikkeukset alta).
   :except    Setti avaimia, joiden nimiä ei määritellä uudelleen X:ssä.
   :custom    Mappi avaimia X:ssä. Arvoilla määritellään eri namespace kuin mitä :ns määrittelee.
   :inner     Mappi avaimia X:ssä, joiden arvo on niin ikään map.
              Arvolla määritellään logiikka, jolla sisemmän mapin avainten nimet muunnetaan.

  Esimerkki:
  Olkoon map:
  {:name \"player1\" :hp 1 :tasks {:id 666 :time 5} :points 7 :foobar nil}

  Sen muunnos:
  (namespacefy data {:ns :our.domain.player ;; Määrittelee oletusnamespacen kaikille avaimille
                     :except [:foobar] ;; :foobar avaimeen ei kosketa
                     :custom {:points :our.domain.point/points} ;; :points avaimelle määritellään poikkeava namespace
                     :inner {:tasks {:ns :our.domain.task}}}) ;; :tasks sisällölle muunnoslogiikka samalla tavalla
  {:our.domain.player/name \"player1\"
   :our.domain.player/hp 1
   :our.domamain.player/tasks {:our.domain.task/id 666
                               :our.domain.task/time 5}
   :our.domain.point/points 7
   :foobar nil}"
  [map-x {:keys [ns except custom inner] :as options}]
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
        map-x-with-modified-inner-maps (merge map-x namespacefied-inner-maps)
        final-rename-logic (merge original-keyword->namespaced-keyword custom)]
    (set/rename-keys map-x-with-modified-inner-maps final-rename-logic)))
