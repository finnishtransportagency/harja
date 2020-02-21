(ns harja.palvelin.komponentit.todennus
  "Tämä namespace määrittelee käyttäjäidentiteetin todentamisen. Käyttäjän todentaminen
  WWW-palvelussa tehdään KOKA ympäristön antamilla header tiedoilla. Tämä komponentti ei tee
  käyttöoikeustarkistuksia, vaan pelkästään hakee käyttäjälle sallitut käyttöoikeudet
  ja tarkistaa käyttäjän identiteetin."
  (:require [clojure.core.cache :as cache]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [harja.domain
             [oikeudet :as oikeudet]]
            [harja.kyselyt
             [kayttajat :as q]]
            [harja.palvelin.komponentit.tapahtumat :refer [kuuntele!]]
            [slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as log])
  (:import (org.apache.commons.codec.net BCodec)))

(def todennusvirhe {:virhe :todennusvirhe})

(defn- ryhman-rooli-ja-linkki
  "Etsii annetulle OAM ryhmälle roolin. Ryhmä voi olla suoraan roolin nimi
tai linkitetyssä roolissa muotoa <linkitetty id>_<roolin nimi>. Palauttaa
roolin tiedot ja linkitetyn id:n vektorissa, jos rooli ei ole linkitetty id
on nil."
  [roolit ryhma]
  (let [roolit (vals roolit)
        ryhmanimet (into #{}
                         (map :nimi roolit))]
    (some (fn [{:keys [nimi linkki] :as rooli}]
            (cond
              (= nimi ryhma)
              [rooli nil]

              (and (not (ryhmanimet ryhma)) linkki (str/ends-with? ryhma (str "_" nimi)))
              [rooli (str/trim (first (str/split ryhma #"_")))]))
          roolit)))

(defn- yleisroolit [roolit-ja-linkit]
  (into #{}
        ;; Haetaan kaikki roolit, joilla ei ole linkkiä
        (comp (map first)
              (filter (comp empty? :linkki))
              (map :nimi))
        roolit-ja-linkit))

(defn- roolien-nimet
  [roolit]
  (into #{}
        (map (comp :nimi first))
        roolit))

(defn poista-nil-id []
  (filter #(some? (first %))))

(defn- urakkaroolit [urakan-id roolit-ja-linkit]
  (into {}
        (comp
         ;; Muuta key Sampo id:stä Harjan urakka id:ksi
         (map #(update-in % [0] urakan-id))
         (poista-nil-id)
         ;; Muuta [[rooli id] ...] -> #{nimi ...}
         (map #(update-in % [1] roolien-nimet)))
        ;; Valitaan vain "urakka" linkitetyt roolit ja
        ;; ryhmitellään ne id:n perusteella
        (group-by second
                  (filter (comp #(= "urakka" %)
                                :linkki
                                first)
                          roolit-ja-linkit))))

(defn organisaatioroolit [urakoitsijan-id roolit-ja-linkit]
  (into {}
        (comp
         (map #(update-in % [0] urakoitsijan-id))
         ;; Poistetaan roolit, joille ei löydy organisaatiota.
         ;; Muuten muiden järjestelmien roolit (esim. Extranet_Liito_Kayttaja) rooli voi sekoittua
         ;; Harjan rooleihin.
         (poista-nil-id)
         (map #(update-in % [1] roolien-nimet)))
        (group-by second
                  (filter (comp #(= "urakoitsija" %) :linkki first)
                          roolit-ja-linkit))))
(defn kayttajan-roolit
  "Palauttaa annetun käyttäjän roolit OAM_GROUPS header arvon perusteella.
  Roolit on mäppäys roolinimestä sen tietoihin. Sähken antama urakan tai
  urakoitsijan id muutetaan harjan id:ksi kutsumalla annettuja urakan-id
  ja urakoitsijan-id funktioita."
  [urakan-id urakoitsijan-id roolit oam-groups]
  (let [roolit-ja-linkit (->> (str/split oam-groups #",")
                              (keep (partial ryhman-rooli-ja-linkki roolit)))]
    {:roolit (yleisroolit roolit-ja-linkit)
     :urakkaroolit (urakkaroolit urakan-id roolit-ja-linkit)
     :organisaatioroolit (organisaatioroolit urakoitsijan-id roolit-ja-linkit)}))

;; Pidetään käyttäjätietoja muistissa vartti, jotta ei tarvitse koko ajan hakea tietokannasta
;; uudestaan. KOKA->käyttäjätiedot pitää hakea joka ikiselle HTTP pyynnölle.
(def kayttajatiedot-cache-atom (atom (cache/ttl-cache-factory {} :ttl (* 15 60 1000))))

(defn- pura-header-arvo
  "KOKA lähettää ääkkösellisen headerin muodossa \"=?UTF?B?...base64...?=\"."
  [teksti]
  (if (and teksti (str/starts-with? teksti "=?"))
    (.decode (BCodec.) teksti)
    teksti))

(defn- koka-headerit [headerit]
  (reduce-kv
   (fn [m k v]
     (assoc m k (pura-header-arvo v)))
   {}
   (select-keys headerit
                [;; Käyttäjätunnus ja ryhmät
                 "oam_remote_user" "oam_groups"
                 ;; ELY-numero (tai null), org nimi ja Y-tunnus
                 "oam_departmentnumber" "oam_organization" "oam_user_companyid"
                 ;; Etu- ja sukunimi
                 "oam_user_first_name" "oam_user_last_name"
                 ;; Sähköposti ja puhelin
                 "oam_user_mail" "oam_user_mobile"])))

(defn- hae-organisaatio-elynumerolla [db ely]
  (some->> ely
           (re-matches #"\d+")
           Long/parseLong
           (q/hae-ely-numerolla db)
           first))

(defn- hae-organisaatio-nimella [db nimi]
  (first (q/hae-organisaatio-nimella db nimi)))

(defn- hae-organisaatio-liitetylle-roolille [db roolit]
  (some->> roolit
           :organisaatioroolit
           keys
           first
           (q/hae-organisaatio-idlla db)
           first))

(defn- hae-organisaatio-y-tunnuksella [db y-tunnus]
  (some->> y-tunnus
           (q/hae-organisaatio-y-tunnuksella db)
           first))

(defn- hae-kayttajalle-organisaatio
  [db ely y-tunnus organisaatio roolit]
  (or
   ;; Jos ELY-numero haetaan se
   (hae-organisaatio-elynumerolla db ely)
   ;; Jos yrityksen Y-tunnus annettu, hae sillä
   (hae-organisaatio-y-tunnuksella db y-tunnus)
   ;; Muuten haetaan org. nimellä
   (hae-organisaatio-nimella db organisaatio)
   ;; Muuten etsitään urakoitsijakohtaista roolia
   (hae-organisaatio-liitetylle-roolille db roolit)))


(defn- varmista-kayttajatiedot
  "Ottaa tietokannan ja käyttäjän OAM headerit. Varmistaa että käyttäjä on olemassa
ja palauttaa käyttäjätiedot"
  [db {kayttajanimi "oam_remote_user"
       ryhmat "oam_groups"
       ely "oam_departmentnumber"
       organisaatio "oam_organization"
       etunimi "oam_user_first_name"
       sukunimi "oam_user_last_name"
       sahkoposti "oam_user_mail"
       puhelin "oam_user_mobile"
       y-tunnus "oam_user_companyid"
       :as headerit}]

  ;; Järjestelmätunnuksilla ei saa kirjautua varsinaiseen Harjaan
  (println "onko-jarjestelma?" kayttajanimi "->" (q/onko-jarjestelma? db kayttajanimi))
  (if (q/onko-jarjestelma? db kayttajanimi)
    (throw+ todennusvirhe)
    (let [roolit (kayttajan-roolit (partial q/hae-urakan-id-sampo-idlla db)
                                   (partial q/hae-urakoitsijan-id-ytunnuksella db)
                                   oikeudet/roolit
                                   ryhmat)
          organisaatio (hae-kayttajalle-organisaatio db ely y-tunnus organisaatio roolit)

         kayttaja {:kayttajanimi kayttajanimi
                   :etunimi etunimi
                   :sukunimi sukunimi
                   :sahkoposti sahkoposti
                   :puhelin puhelin
                   :organisaatio (:id organisaatio)}
         kayttaja-id (q/varmista-kayttaja
                       db
                       (assoc kayttaja
                         :organisaatio (:id organisaatio)))]
     (log/info "SÄHKE HEADERIT: " headerit
               "; KÄYTTÄJÄ ID: " kayttaja-id
               "; ORGANISAATIO: " organisaatio)
     (merge (assoc kayttaja
              :organisaatio organisaatio
              :organisaation-urakat (into #{}
                                          (map :id)
                                          (q/hae-organisaation-urakat db (:id organisaatio)))
              :id kayttaja-id)
            roolit))))

(defn- ohita-oikeudet
  "Mahdollista kaikkien OAM_* headerien ohittaminen tietyille käyttäjille konfiguraatiossa.
Jos käyttäjälle on ohitetut headerit, ne palautetaan KOKAn antamien headerien sijasta, muuten
headerit palautetaan normaalisti."
  [{kayttaja "oam_remote_user" :as koka-headerit} oikeudet]
  (or (and oikeudet (oikeudet kayttaja))
      koka-headerit))

(defn koka->kayttajatiedot [db headerit oikeudet]
  (let [oam-tiedot (ohita-oikeudet (koka-headerit headerit) oikeudet)]
    (try
      (get (swap! kayttajatiedot-cache-atom
                  #(cache/through
                    (fn [oam-tiedot]
                      (varmista-kayttajatiedot db oam-tiedot))
                    %
                    oam-tiedot))
           oam-tiedot)
      (catch Throwable t
        (log/warn t "Käyttäjätietojen varmistuksessa virhe!")))))

(defprotocol Todennus
  "Protokolla HTTP pyyntöjen käyttäjäidentiteetin todentamiseen."
  (todenna-pyynto [this req] "Todenna annetun HTTP-pyynnön käyttäjätiedot, palauttaa uuden
req mäpin, jossa käyttäjän tiedot on lisätty avaimella :kayttaja."))

(defrecord HttpTodennus [oikeudet]
  component/Lifecycle
  (start [this]
    (log/info "Todennetaan HTTP käyttäjä KOKA headereista.")
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [{db :db :as this} req]
    (let [headerit (:headers req)
          _ (log/warn "TEMP DEBUG. headerit " headerit)
          kayttaja-id (headerit "oam_remote_user")
          _ (log/warn "TEMP DEBUG. kayttaja-id " kayttaja-id)
          ]

      (if (nil? kayttaja-id)
        (do
          (log/warn "Todennusheader oam_remote_user puuttui kokonaan")
          (throw+ todennusvirhe))
        (if-let [kayttajatiedot (koka->kayttajatiedot db headerit oikeudet)
                 _ (log/warn "TEMP DEBUG. kayttajatiedot " kayttajatiedot)
                 _ (log/warn "TEMP DEBUG. oikeudet " oikeudet)
                 _ (log/warn "TEMP DEBUG. headerit " headerit)
                 ]
          (assoc req :kayttaja kayttajatiedot)
          (do
            (log/warn "Ei löydetty koka-käyttäjätietoja id:lle" (pr-str (headerit "oam_remote_user")))
            (throw+ todennusvirhe)))))))

(defrecord FeikkiHttpTodennus [kayttaja]
  component/Lifecycle
  (start [this]
    (log/warn "Käytetään FEIKKI käyttäjätodennusta, käyttäjä = " (pr-str kayttaja))
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [this req]
    (assoc req
      :kayttaja kayttaja)))

(defn http-todennus
  ([] (http-todennus nil))
  ([oikeudet]
   (->HttpTodennus oikeudet)))

(defn feikki-http-todennus [kayttaja]
  (->FeikkiHttpTodennus kayttaja))
