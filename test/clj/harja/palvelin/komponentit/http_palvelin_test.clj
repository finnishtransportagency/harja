(ns harja.palvelin.komponentit.http-palvelin-test
  (:require [harja.testi :refer :all]
            [clojure.test :refer :all]
            [org.httpkit.client :as http]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as palvelin]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.index :as index]
            [harja.kyselyt.anti-csrf :as anti-csrf-q]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [clj-time.core :as t]))

(def kayttaja +kayttaja-jvh+)

(def csrf-token-secret "foobar")

(def random-avain "baz")

(def csrf-token (index/muodosta-csrf-token random-avain
                                           csrf-token-secret))
(defn jarjestelma-fixture [testit]
  (let [nyt (t/now)]
    (pudota-ja-luo-testitietokanta-templatesta)
    (alter-var-root #'portti (fn [_#] (arvo-vapaa-portti)))
    (alter-var-root #'jarjestelma
                    (fn [_]
                      (component/start
                        (component/system-map
                          :todennus (component/using
                                      (todennus/http-todennus {})
                                      [:db])
                          :db (tietokanta/luo-tietokanta testitietokanta)
                          :http-palvelin (component/using
                                           (http-palvelin/luo-http-palvelin {:portti portti
                                                                             :anti-csrf-token csrf-token-secret} true)
                                           [:todennus :db])))))
    (anti-csrf-q/poista-ja-luo-csrf-sessio (:db jarjestelma) (:kayttajanimi kayttaja) csrf-token nyt)
    (testit)
    (alter-var-root #'jarjestelma component/stop)))

(use-fixtures :each jarjestelma-fixture)

(def headers {"OAM_REMOTE_USER" (:kayttajanimi kayttaja)
              "OAM_GROUPS" (interpose "," (:roolit kayttaja))
              "Content-Type" "application/json"
              "x-csrf-token" random-avain})

(defn- get-kutsu [palvelu]
  @(http/get (str "http://localhost:" portti "/_/" (name palvelu))
             {:headers headers}))

(defn- post-kutsu [palvelu body]
  @(http/post (str "http://localhost:" portti "/_/" (name palvelu))
              {:headers headers
               :body body}))

(defn- julkaise-ja-testaa [nimi get? palvelu-fn odotettu-status odotettu-body]
  (palvelin/julkaise-palvelu (:http-palvelin jarjestelma) nimi palvelu-fn)
  (let [vastaus (if get? (get-kutsu nimi) (post-kutsu nimi "{}"))]
    (println "petar vastaus ")
    (clojure.pprint/pprint vastaus)
    (is (= odotettu-status (:status vastaus)))
    (is (.contains (:body vastaus) odotettu-body))))

(deftest get-palvelu-palauta-ok
  (julkaise-ja-testaa :ok-palvelu-get true (fn [user] "kaikki OK") 200 "kaikki OK"))

(deftest post-palvelu-palauta-ok
  (julkaise-ja-testaa :ok-palvelu-post false (fn [user _] "kaikki OK") 200 "kaikki OK"))

(deftest get-palvelu-feilaa-sisaisesti
  (julkaise-ja-testaa :huono-palvelu-get true (fn [user] (throw (RuntimeException. "Simuloitu huono palvelu")))
                      500 "Simuloitu huono palvelu"))

(deftest post-palvelu-feilaa-sisaisesti
  (julkaise-ja-testaa :huono-palvelu-post false (fn [user _] (throw (RuntimeException. "Simuloitu huono palvelu")))
                      500 "Simuloitu huono palvelu"))

(deftest get-palvelu-feilaa-kun-on-huono-pyynto
  (julkaise-ja-testaa :huono-pyynto-get true (fn [user] (throw (IllegalArgumentException. "Simuloitu huono pyyntö")))
                      400 "Simuloitu huono pyyntö"))

(deftest post-palvelu-feilaa-kun-on-huono-pyynto
  (julkaise-ja-testaa :huono-pyynto-post false (fn [user _] (throw (IllegalArgumentException. "Simuloitu huono pyyntö")))
                      400 "Simuloitu huono pyyntö"))
