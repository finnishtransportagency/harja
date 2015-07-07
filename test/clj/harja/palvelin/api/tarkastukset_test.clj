(ns harja.palvelin.api.tarkastukset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.api.tarkastukset :as api-tarkastukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]
            [clojure.string :as str]))


(def portti nil)
(def kayttaja "fastroi")
(def urakka nil)

(defn json-pvm [pvm]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") pvm))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'portti (fn [_] (arvo-vapaa-portti)))
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                     (component/system-map
                      :db (apply tietokanta/luo-tietokanta testitietokanta)
                      :klusterin-tapahtumat (component/using
                                             (tapahtumat/luo-tapahtumat)
                                             [:db])
                      
                      :todennus (component/using
                                 (todennus/http-todennus)
                                 [:db :klusterin-tapahtumat])
                      :http-palvelin (component/using
                                      (http-palvelin/luo-http-palvelin portti true)
                                      [:todennus])
                      :api-tarkastukset (component/using
                                         (api-tarkastukset/->Tarkastukset)
                                         [:http-palvelin :db])))))

  (alter-var-root #'urakka
                  (fn [_]
                    (ffirst (q (str "SELECT id FROM urakka WHERE urakoitsija=(SELECT organisaatio FROM kayttaja WHERE kayttajanimi='" kayttaja "') "
                                    " AND tyyppi='hoito'::urakkatyyppi")))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-tiestotarkastus
  (is true))

;; FIXME: generisoi muihin API testeihin tätä sekä fixturea
(defn api-kutsu
  "Tekee POST kutsun APIin. Polku on vektori (esim [\"/api/foo/\" arg \"/bar\"]), joka on palvelimen juureen relatiivinen.
  Body on json string (tai muu http-kitin ymmärtämä input)."
  [api-polku-vec body]
  @(http/post (reduce str (concat ["http://localhost:" portti] api-polku-vec))
              {:body body
               :headers {"OAM_REMOTE_USER" kayttaja
                         "Content-Type" "application/json"}}))

(deftest tallenna-soratietarkastus
  (let [pvm (java.util.Date.)
        id (rand-int 10000) ;; FIXME: varmista että ei ole olemassa
        
        vastaus (api-kutsu ["/api/urakat/" urakka "/tarkastus/soratietarkastus"]
                           (-> "test/resurssit/api/soratietarkastus.json"
                               slurp
                               (.replace "__PVM__" (json-pvm pvm))
                               (.replace "__ID__" (str id))))]
    
    (is (= 200 (:status vastaus)))
    (is (str/blank? (slurp (:body vastaus))))

    ;; varmistetaan että tarkastus löytyy tietokannasta
    (let [tark (first (q (str "SELECT t.tyyppi, h.kuvaus, stm.kiinteys "
                              "  FROM tarkastus t "
                              "       JOIN havainto h ON t.havainto=h.id "
                              "       JOIN soratiemittaus stm ON stm.tarkastus=t.id "
                              " WHERE t.ulkoinen_id = " id
                              "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))]
      (is (= tark ["soratie" "jotain outoa" 3]) (str "Tarkastuksen data tallentunut ok " id)))

    (let [t-id (ffirst (q (str "SELECT id FROM tarkastus"
                               " WHERE ulkoinen_id=" id
                               "   AND luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))
          h-id (ffirst (q (str "SELECT havainto FROM tarkastus WHERE id=" t-id)))]
      (u (str "DELETE FROM soratiemittaus WHERE tarkastus=" t-id))
      (u (str "DELETE FROM tarkastus WHERE id=" t-id))
      (u (str "DELETE FROM havainto WHERE id=" h-id)))))


(deftest tallenna-virheellinen-soratietarkastus
  (let [vastaus (api-kutsu ["/api/urakat/" urakka "/tarkastus/soratietarkastus"]
                           (-> "test/resurssit/api/soratietarkastus-virhe.json"
                               slurp))]
    (is (= 400 (:status vastaus)))
    (is (= "invalidi-json" (some-> vastaus :body json/read-str
                                   (get "virheet") first (get "virhe") (get "koodi"))))))


(deftest tallenna-talvihoitotarkastus
  (let [pvm (java.util.Date.)
        id (rand-int 10000) ;; FIXME: varmista että ei ole olemassa
        
        vastaus (api-kutsu ["/api/urakat/" urakka "/tarkastus/talvihoitotarkastus"]
                           (-> "test/resurssit/api/talvihoitotarkastus.json"
                               slurp
                               (.replace "__PVM__" (json-pvm pvm))
                               (.replace "__ID__" (str id))))]
    
    (is (= 200 (:status vastaus)))
    (is (str/blank? (slurp (:body vastaus))))

    ;; varmistetaan että tarkastus löytyy tietokannasta
    (let [tark (first (q (str "SELECT t.tyyppi, h.kuvaus, thm.lumimaara "
                              "  FROM tarkastus t "
                              "       JOIN havainto h ON t.havainto=h.id "
                              "       JOIN talvihoitomittaus thm ON thm.tarkastus=t.id "
                              " WHERE t.ulkoinen_id = " id
                              "   AND t.luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))]
      (is (= tark ["talvihoito" "jotain talvisen outoa" 15.00M]) (str "Tarkastuksen data tallentunut ok " id)))

    (let [t-id (ffirst (q (str "SELECT id FROM tarkastus"
                               " WHERE ulkoinen_id=" id
                               "   AND luoja = (SELECT id FROM kayttaja WHERE kayttajanimi='" kayttaja "')")))
          h-id (ffirst (q (str "SELECT havainto FROM tarkastus WHERE id=" t-id)))]
      (u (str "DELETE FROM talvihoitomittaus WHERE tarkastus=" t-id))
      (u (str "DELETE FROM tarkastus WHERE id=" t-id))
      (u (str "DELETE FROM havainto WHERE id=" h-id)))))
    
                               
