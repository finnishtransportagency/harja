(ns harja.oikea-http-palvelin-jarjestelma-fixture
  (:require [harja.testi :refer :all]
            [clojure.test :refer :all]
            [org.httpkit.client :as http]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.index :as index]
            [harja.kyselyt.anti-csrf :as anti-csrf-q]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.transit :as tr]
            [clj-time.core :as t]))

(def kayttaja +kayttaja-jvh+)

(def csrf-token-secret "foobar")

(def random-avain "baz")

(def csrf-token (index/muodosta-csrf-token random-avain
                                           csrf-token-secret))

(defn luo-fixture
  "'komponentit' ovat key/value parit, jotka lisätään 'component/system-map' kutsuun"
  [& komponentit]
   (fn [testit]
     (pudota-ja-luo-testitietokanta-templatesta)
     (alter-var-root #'portti (fn [_#] (arvo-vapaa-portti)))
     (let [nyt (t/now)
           oletus-komponentit [:todennus (component/using
                                           (todennus/http-todennus {})
                                           [:db])
                               :db (tietokanta/luo-tietokanta testitietokanta)
                               :http-palvelin (component/using
                                                (http-palvelin/luo-http-palvelin {:portti          portti
                                                                                  :anti-csrf-token csrf-token-secret} true)
                                                [:todennus :db])]
           kaikki-komponentit (vec (concat oletus-komponentit komponentit))]
       (alter-var-root #'jarjestelma
                       (fn [_]
                         (component/start
                           (apply component/system-map kaikki-komponentit))))
       (anti-csrf-q/poista-ja-luo-csrf-sessio (:db jarjestelma) (:kayttajanimi kayttaja) csrf-token nyt)
       (testit)
       (alter-var-root #'jarjestelma component/stop))))

(def headers {"OAM_REMOTE_USER" (:kayttajanimi kayttaja)
              "OAM_GROUPS" (interpose "," (:roolit kayttaja))
              "Content-Type" "application/json"
              "x-csrf-token" random-avain})

(defn get-kutsu [palvelu]
  @(http/get (str "http://localhost:" portti "/_/" (name palvelu))
             {:headers headers}))

(defn post-kutsu [palvelu body]
  @(http/post (str "http://localhost:" portti "/_/" (name palvelu))
              {:headers headers
               :body (tr/clj->transit body)}))
