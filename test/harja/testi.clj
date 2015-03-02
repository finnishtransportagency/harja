(ns harja.testi
  "Harjan testauksen apukoodia."
  (:require [harja.palvelin.komponentit.http-palvelin :as http]))


(def testitietokanta [(if (= "harja-jenkins.solitaservices.fi"
                             (.getHostName (java.net.InetAddress/getLocalHost)))
                        "172.17.238.100"
                        "localhost")
                      5432
                      "harjatest"
                      "harjatest"
                      nil])

(defprotocol FeikkiHttpPalveluKutsu
  (kutsu-palvelua
    ;; GET
    [this nimi kayttaja]
                                        
    ;; POST
    [this nimi kayttaja payload]
    "kutsu HTTP palvelufunktiota suoraan."))
    
(defn testi-http-palvelin
  "HTTP 'palvelin' joka vain ottaa talteen julkaistut palvelut."
  []
  (let [palvelut (atom {})]
    (reify
      http/HttpPalvelut
      (julkaise-palvelu [_ nimi palvelu-fn]
        (swap! palvelut assoc nimi palvelu-fn))
      (julkaise-palvelu [_ nimi palvelu-fn optiot]
        (swap! palvelut assoc nimi palvelu-fn))
      (poista-palvelu [_ nimi]
        (swap! palvelut dissoc nimi))

      FeikkiHttpPalveluKutsu
      (kutsu-palvelua [_ nimi kayttaja]
        ((get @palvelut nimi) kayttaja))
      (kutsu-palvelua [_ nimi kayttaja payload]
        ((get @palvelut nimi) kayttaja payload)))))
  

;; Määritellään käyttäjiä, joita testeissä voi käyttää
;; HUOM: näiden pitää täsmätä siihen mitä testidata.sql tiedostossa luodaan.

;; id:1 Tero Toripolliisi, POP ELY aluevastaava
(def +kayttaja-tero+ {:id 1 :etunimi "Tero" :sukunimi "Toripolliisi" :kayttajanimi "LX123456789"})

;; id:2 Järjestelmävastuuhenkilö
(def +kayttaja-jvh+ {:id 2 :etunimi "Jalmari" :sukunimi "Järjestelmävastuuhenkilö" :kayttajanimi "jvh"})

;; Tätä käytetään testikäyttäjien määrän tarkistamiseen. Tätä pitää kasvattaa jos testidataan lisää uusia.
(def +testikayttajia+ 2)
