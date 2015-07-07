(ns harja.palvelin.palvelut.laadunseuranta
  "Laadunseuranta: Tarkastukset, Havainnot ja Sanktiot"

  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]

            [harja.kyselyt.havainnot :as havainnot]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.sanktiot :as sanktiot]
            [harja.kyselyt.tarkastukset :as tarkastukset]

            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
             
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]))

(def havainto-xf (comp
                   (map konv/alaviiva->rakenne)
                   (map #(assoc % :tekija (keyword (:tekija %))))
                   (map #(update-in % [:paatos :paatos]
                                    (fn [p]
                                      (when p (keyword p)))))
                   (map #(update-in % [:paatos :kasittelytapa]
                                    (fn [k]
                                      (when k (keyword k)))))))

(def tarkastus-xf
  (comp
   (map konv/alaviiva->rakenne)
   (map #(konv/string->keyword % :tyyppi [:havainto :tekija]))
   (map #(-> %1
             (assoc-in [:havainto :selvitys-pyydetty] (get-in %1 [:havainto :selvitys-pyydetty]))
             (update-in [:havainto] dissoc :selvityspyydetty)
             (update-in [:havainto] (fn [h]
                                      (if (nil? (:selvitys-pyydetty h))
                                        (dissoc h :selvitys-pyydetty)
                                        h)))))

   (map #(dissoc % :sopimus)) ;; tarvitaanko sopimusta?
   (map (fn [tarkastus]
          (condp = (:tyyppi tarkastus)
            :talvihoito (dissoc tarkastus :soratiemittaus)
            :soratie (dissoc tarkastus :talvihoitomittaus)
            :tiesto (dissoc tarkastus :soratiemittaus :talvihoitomittaus))))))


(defn hae-urakan-havainnot [db user {:keys [listaus urakka-id alku loppu]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [parametrit [db urakka-id (konv/sql-timestamp alku) (konv/sql-timestamp loppu)]]
    (into []
          havainto-xf

          (if (= :omat listaus)
            (apply havainnot/hae-omat-havainnot (conj parametrit (:id user)))
            (apply (case listaus
                     :kaikki havainnot/hae-kaikki-havainnot
                     :selvitys havainnot/hae-selvitysta-odottavat-havainnot
                     :kasitellyt havainnot/hae-kasitellyt-havainnot) parametrit)))))

(defn- luo-tai-paivita-havainto
  "Luo uuden havainnon tai päivittää olemassaolevan havainnon perustiedot. Palauttaa havainnon id:n."
  [db user {:keys [id kohde tekija urakka aika selvitys-pyydetty kuvaus] :as havainto}]
  (if id
    (do (havainnot/paivita-havainnon-perustiedot! db
                                                  (konv/sql-timestamp aika) (name tekija) kohde
                                                  (if selvitys-pyydetty true false)
                                                  (:id user)
                                                  kuvaus
                                                  id)
        id)

    (:id (havainnot/luo-havainto<! db urakka (konv/sql-timestamp aika) (name tekija) kohde
                                   (if selvitys-pyydetty true false) (:id user) kuvaus
                                   nil nil nil nil nil nil nil nil))))


(defn hae-havainnon-tiedot
  "Hakee yhden havainnon kaiken tiedon muokkausnäkymää varten: havainnon perustiedot, kommentit ja liitteet, päätös ja sanktiot.
   Ottaa urakka-id:n ja havainto-id:n. Urakka id:tä käytetään oikeustarkistukseen, havainnon tulee olla annetun urakan
   toimenpiteeseen kytketty."
  [db user urakka-id havainto-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [havainto (first (into []
                              havainto-xf
                              (havainnot/hae-havainnon-tiedot db urakka-id havainto-id)))]
    (when havainto
      (assoc havainto
        :kommentit (into []
                         (comp (map konv/alaviiva->rakenne)
                               (map #(assoc % :tekija (name (:tekija %))))
                               (map (fn [{:keys [liite] :as kommentti}]
                                      (if (:id liite)
                                        kommentti
                                        (dissoc kommentti :liite)))))
                         (havainnot/hae-havainnon-kommentit db havainto-id))
        :sanktiot (into []
                        (comp (map #(konv/array->set % :tyyppi_sanktiolaji keyword))
                              (map konv/alaviiva->rakenne)
                              (map #(konv/string->keyword % :laji))
                              (map #(assoc %
                                     :sakko? (not (nil? (:summa %)))
                                     :summa (some-> % :summa double))))
                        (sanktiot/hae-havainnon-sanktiot db havainto-id))))))


(defn tallenna-havainnon-sanktio [db user {:keys [id perintapvm laji tyyppi toimenpideinstanssi summa indeksi] :as sanktio} havainto urakka]
  (log/debug "TALLENNA sanktio: " sanktio " urakka: " urakka ", tyyppi: " tyyppi)
  (if (neg? id)
    (let [id (:id (sanktiot/luo-sanktio<! db (konv/sql-timestamp perintapvm)
                                          (name laji) (:id tyyppi)
                                          toimenpideinstanssi urakka
                                          summa indeksi havainto))]
      (sanktiot/merkitse-maksuera-likaiseksi! db havainto))
    ;; FIXME: voiko päivittää sanktiota?
    nil))


(defn tallenna-havainto [db user {:keys [urakka] :as havainto}]
  (log/info "Tuli havainto: " havainto)
  (roolit/vaadi-rooli-urakassa user roolit/havaintojen-kirjaus urakka)
  (jdbc/with-db-transaction [c db]

                            (let [osapuoli (roolit/osapuoli user urakka)
                                  havainto (assoc havainto
                                             ;; Jos osapuoli ei ole urakoitsija, voidaan asettaa selvitys-pyydetty päälle
                                             :selvitys-pyydetty (and (not= :urakoitsija osapuoli)
                                                                     (:selvitys-pyydetty havainto))

                                             ;; Jos urakoitsija kommentoi, asetetaan selvitys annettu
                                             :selvitys-annettu (and (:uusi-kommentti havainto)
                                                                    (= :urakoitsija osapuoli)))
                                  id (luo-tai-paivita-havainto c user havainto)]
                              ;; Luodaan uudet kommentit
                              (when-let [uusi-kommentti (:uusi-kommentti havainto)]
                                (log/info "UUSI KOMMENTTI: " uusi-kommentti)
                                (let [liite (some->> uusi-kommentti
                                                     :liite
                                                     :id
                                                     (liitteet/hae-urakan-liite-id c urakka)
                                                     first
                                                     :id)
                                      kommentti (kommentit/luo-kommentti<! c
                                                                           (name (:tekija havainto))
                                                                           (:kommentti uusi-kommentti)
                                                                           liite
                                                                           (:id user))]
                                  ;; Liitä kommentti havaintoon
                                  (havainnot/liita-kommentti<! c id (:id kommentti))))


                              (when (:paatos (:paatos havainto))
                                ;; Urakanvalvoja voi kirjata päätöksen
                                (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka)
                                (log/info "Kirjataan päätös havainnolle: " id ", päätös: " (:paatos havainto))
                                (let [{:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos havainto)]
                                  (havainnot/kirjaa-havainnon-paatos! c
                                                                      (konv/sql-timestamp kasittelyaika)
                                                                      (name paatos) perustelu
                                                                      (name kasittelytapa) muukasittelytapa
                                                                      (:id user)
                                                                      id))
                                (when (= :sanktio (:paatos (:paatos havainto)))
                                  (doseq [sanktio (:sanktiot havainto)]
                                    (tallenna-havainnon-sanktio c user sanktio id urakka))))

                              (hae-havainnon-tiedot c user urakka id))))

(defn hae-urakan-sanktiot
  "Hakee urakan sanktiot perintäpvm:n mukaan"
  [db user {:keys [urakka-id alku loppu]}]

  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(konv/decimal->double % :summa)))
        (sanktiot/hae-urakan-sanktiot db urakka-id (konv/sql-timestamp alku) (konv/sql-timestamp loppu))))

(defn hae-sanktiotyypit
  "Palauttaa kaikki sanktiotyypit, hyvin harvoin muuttuvaa dataa."
  [db user]
  (into []
        ;; Muunnetaan sanktiolajit arraysta, keyword setiksi
        (map #(konv/array->set % :laji keyword))
        (sanktiot/hae-sanktiotyypit db)))


(defn hae-urakan-tarkastukset
  "Palauttaa urakan tarkastukset annetulle aikavälille."
  [db user {:keys [urakka-id alkupvm loppupvm tienumero]}]
  (into []
        tarkastus-xf
        (tarkastukset/hae-urakan-tarkastukset db urakka-id
                                              (konv/sql-timestamp alkupvm)
                                              (konv/sql-timestamp loppupvm)
                                              (if tienumero true false) tienumero)))

(defn hae-tarkastus [db user urakka-id tarkastus-id]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (first (into [] tarkastus-xf (tarkastukset/hae-tarkastus db urakka-id tarkastus-id))))

(defn tallenna-tarkastus [db user urakka-id tarkastus]
  (roolit/vaadi-rooli-urakassa user roolit/havaintojen-kirjaus urakka-id)
  (try 
    (jdbc/with-db-transaction [c db]
      (let [havainto (merge (:havainto tarkastus)
                            {:aika (:aika tarkastus)
                             :urakka urakka-id})
            
            uusi? (nil? (:id tarkastus))
            havainto-id (luo-tai-paivita-havainto c user havainto)
            id (tarkastukset/luo-tai-paivita-tarkastus c user urakka-id tarkastus
                                                       havainto-id)]
        
        (condp = (:tyyppi tarkastus)
          :talvihoito (tarkastukset/luo-tai-paivita-talvihoitomittaus c id uusi? (:talvihoitomittaus tarkastus))
          :soratie (tarkastukset/luo-tai-paivita-soratiemittaus c id uusi? (:soratiemittaus tarkastus))
          nil)
        
        
        (log/info "SAATIINPA urakalle " urakka-id " tarkastus: " tarkastus)
        (hae-tarkastus c user urakka-id id)))
    (catch Exception e
      (log/info e "Tarkastuksen tallennuksessa poikkeus!"))))

(defrecord Laadunseuranta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]

    (julkaise-palvelut
     http-palvelin
     
     :hae-urakan-havainnot
     (fn [user tiedot]
       (hae-urakan-havainnot db user tiedot))
     
     :tallenna-havainto
     (fn [user havainto]
       (tallenna-havainto db user havainto))
     
     :hae-havainnon-tiedot
     (fn [user {:keys [urakka-id havainto-id]}]
       (hae-havainnon-tiedot db user urakka-id havainto-id))
     
     :hae-urakan-sanktiot
     (fn [user tiedot]
       (hae-urakan-sanktiot db user tiedot))
     
     :hae-sanktiotyypit
     (fn [user]
       (hae-sanktiotyypit db user))
     
     :hae-urakan-tarkastukset
     (fn [user tiedot]
       (hae-urakan-tarkastukset db user tiedot))

     :tallenna-tarkastus
     (fn [user {:keys [urakka-id tarkastus]}]
       (tallenna-tarkastus db user urakka-id tarkastus))


     :hae-tarkastus
     (fn [user {:keys [urakka-id tarkastus-id]}]
       (hae-tarkastus db user urakka-id tarkastus-id)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
                     :hae-urakan-havainnot
                     :tallenna-havainto
                     :hae-havainnon-tiedot
                     :hae-urakan-sanktiot
                     :hae-sanktiotyypit
                     :hae-urakan-tarkastukset
                     :tallenna-tarkastus
                     :hae-tarkastus)
    this))
            
