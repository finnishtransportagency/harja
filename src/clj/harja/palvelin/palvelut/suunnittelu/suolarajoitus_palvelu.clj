(ns harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.tyokalut :as tyokalut]
            [slingshot.slingshot :refer [throw+ try+]]))

;; TODO: siirrä tämä pvm namespaceen. Ja tarkista onko sitä olemassa. -> tulevat-hoitokaudet tiedot.urakka namespacessa
(defn tulevat-hoitovuodet
  "Palauttaa nykyvuosi ja loppupv välistä urakan hoitovuodet vectorissa tyyliin: [2020 2021 2022 2023 2024].
  Jos tuleville voisille ei kopioida mitään, palauttaa vectorissa vain nykyvuoden tyyliin: [2022]"
  [nykyvuosi kopioidaan-tuleville-vuosille? urakka]
  (let [urakan-loppuvuosi (pvm/vuosi (:loppupvm urakka))
        hoitovuodet (if kopioidaan-tuleville-vuosille?
                      (range nykyvuosi urakan-loppuvuosi)
                      [nykyvuosi])]
    hoitovuodet))

(defn hae-suolarajoitukset [db user {:keys [urakka_id hoitokauden_alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka_id)
  (let [_ (log/debug "Suolarajoitus :: hae-suolarajoitukset :: tiedot " tiedot)
        rajoitukset (suolarajoitus-kyselyt/hae-suolarajoitukset-hoitokaudelle db
                      {:hoitokauden_alkuvuosi hoitokauden_alkuvuosi
                       :urakka_id urakka_id})
        rajoitukset (mapv (fn [suolarajoitus]
                            (update suolarajoitus :pohjavesialueet
                              (fn [alueet]
                                (mapv
                                  #(konv/pgobject->map % :tunnus :string :nimi :string)
                                  (konv/pgarray->vector alueet)))))
                      rajoitukset)
        _ (log/debug "Suolarajoitus :: hae-suolarajoitukset :: Löydettiin rajoitukset:" rajoitukset)]
    rajoitukset))

(defn tallenna-suolarajoitus
  "Suolarajoitukset on tallennettu kahteen eri tietokantatauluun.
   Rajoitusalue -taulu sisältää sijaintitiedot ja rajoitusalue_rajoitus sisältää hoitovuosikohtaiset tiedot.
   Pohjavesialueita ei tallenneta rajoitukselle ollenkaan, vaan ne haetaan tierekisteriosoitteen perusteella aina tarvittaessa lennossa."
  [db user suolarajoitus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user (:urakka_id suolarajoitus))
  (log/debug "tallenna-suolarajoitus :: suolarajoitus" suolarajoitus)
  (let [kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? suolarajoitus)
        urakan-hoitovuodet (tulevat-hoitovuodet
                             (:hoitokauden_alkuvuosi suolarajoitus)
                             kopioidaan-tuleville-vuosille?
                             (first (urakat-kyselyt/hae-urakka db {:id (:urakka_id suolarajoitus)})))
        ;; Tallennetaan ensin rajoitusalue uutena tai päivityksenä
        db-rajoitusalue {:id (when (:rajoitusalue_id suolarajoitus) (:rajoitusalue_id suolarajoitus))
                         :tie (:tie suolarajoitus)
                         :aosa (:aosa suolarajoitus)
                         :aet (:aet suolarajoitus)
                         :losa (:losa suolarajoitus)
                         :let (:let suolarajoitus)
                         :pituus (:pituus suolarajoitus)
                         :ajoratojen_pituus (:ajoratojen_pituus suolarajoitus)
                         :urakka_id (:urakka_id suolarajoitus)
                         :kayttaja_id (:id user)}

        ;; Päivitä tai tallenna uutena
        rajoitusalue (if (:id db-rajoitusalue)
                       (do
                         (suolarajoitus-kyselyt/paivita-rajoitusalue! db db-rajoitusalue)
                         (first (suolarajoitus-kyselyt/hae-suolarajoitusalue db {:id (:rajoitusalue_id suolarajoitus)})))
                       (let [vastaus (suolarajoitus-kyselyt/tallenna-rajoitusalue<! db (dissoc db-rajoitusalue :id))]
                         (first (suolarajoitus-kyselyt/hae-suolarajoitusalue db {:id (:id vastaus)}))))

        db-rajoitus {:id (when (:rajoitus_id suolarajoitus) (:rajoitus_id suolarajoitus))
                     :rajoitusalue_id (:id rajoitusalue)
                     :suolarajoitus (:suolarajoitus suolarajoitus)
                     :formiaatti (:formiaatti suolarajoitus)
                     :kayttaja_id (:id user)}
        ;; Päivitä tai tallenna uutena
        suolarajoitus (if (:id db-rajoitus)
                        (do
                          (mapv (fn [vuosi]
                                  (let [;; Haetaan päivitettävä rajoitus tietokannasta urakan ja vuoden perusteella. Meillä ei ole id:tä kaikille vuosille
                                        haettu-rajoitus (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                                                            :hoitokauden_alkuvuosi vuosi}))
                                        rajoitus {:id (:rajoitus_id haettu-rajoitus)
                                                  :kayttaja_id (:id user)
                                                  :suolarajoitus (:suolarajoitus suolarajoitus)
                                                  :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi haettu-rajoitus)
                                                  :formiaatti (:formiaatti suolarajoitus)
                                                  :rajoitusalue_id (:rajoitusalue_id haettu-rajoitus)}
                                        _ (suolarajoitus-kyselyt/paivita-suolarajoitus! db rajoitus)]
                                    rajoitus))
                            urakan-hoitovuodet)
                          (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                              :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi suolarajoitus)})))
                        (do
                          (mapv (fn [vuosi]
                                  (let [rajoitus (assoc db-rajoitus :hoitokauden_alkuvuosi vuosi)
                                        r (suolarajoitus-kyselyt/tallenna-suolarajoitus<! db (dissoc rajoitus :id))]
                                    rajoitus))
                            urakan-hoitovuodet)
                          (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                              :hoitokauden_alkuvuosi (:hoitokauden_alkuvuosi suolarajoitus)}))))

        suolarajoitus (update suolarajoitus :pohjavesialueet
                        (fn [alueet]
                          (mapv
                            #(konv/pgobject->map % :tunnus :string :nimi :string)
                            (konv/pgarray->vector alueet))))]
    suolarajoitus))

(defn poista-suolarajoitus [db user {:keys [kopioidaan-tuleville-vuosille? urakka_id rajoitusalue_id hoitokauden_alkuvuosi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka_id)
  (let [_ (log/debug "Suolarajoitus :: poista-suolarajoitus :: rajoitusalue_id:" rajoitusalue_id " hoitokauden_alkuvuosi: " hoitokauden_alkuvuosi)
        ;; Poistaa kaikki suolarajoitukset joilla on sama tai suurempi hoitokauden alkuvuosi, mikäli kopiointi on käytössä
        poistetut-rajoitukset (suolarajoitus-kyselyt/poista-suolarajoitus<! db (merge {:rajoitusalue_id rajoitusalue_id
                                                                                       :hoitokauden_alkuvuosi hoitokauden_alkuvuosi
                                                                                       :poista-tulevat nil}
                                                                                 (when kopioidaan-tuleville-vuosille?
                                                                                   {:poista-tulevat "true"})))
        ;; Jos suolarajoituksia ei jää rajoitusalue_rajoitus tauluun, niin poistetaan myös alkuperäinen rajoitusalue
        suolarajoitukset (suolarajoitus-kyselyt/hae-suolarajoitukset-rajoitusalueelle db {:rajoitusalue_id rajoitusalue_id})
        poistetut-rajoitusalueet (when (empty? suolarajoitukset)
                                   (suolarajoitus-kyselyt/poista-suolarajoitusalue<! db {:id rajoitusalue_id}))]
    (if (nil? poistetut-rajoitukset)
      (transit-vastaus 400 {:virhe "Suolarajoituksen poistaminen epäonnistui"})
      "OK")))

(defn pituuden-laskennan-data-validi?
  [{:keys [tie aosa losa aet let] :as suolarajoitus}]
  (and
    (pos-int? tie)
    (int? aosa)
    (int? losa)
    (int? aet)
    (int? let)))

(defn tierekisterin-tiedot [db user suolarajoitus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user (:urakka_id suolarajoitus))
  (if (pituuden-laskennan-data-validi? suolarajoitus)
    (let [;; Pilkotaan tierekisteri osiin tien osien mukaan
          tie-osien-pituudet (tieverkko-kyselyt/hae-osien-pituudet db {:tie (:tie suolarajoitus)
                                                                       :aosa (:aosa suolarajoitus)
                                                                       :losa (:losa suolarajoitus)})
          pituus (tieverkko-kyselyt/laske-tien-osien-pituudet tie-osien-pituudet suolarajoitus)
          ajoratojen-pituudet (tieverkko-kyselyt/hae-ajoratojen-pituudet db {:tie (:tie suolarajoitus)
                                                                             :aosa (:aosa suolarajoitus)
                                                                             :losa (:losa suolarajoitus)})
          ajoratojen-pituus (reduce (fn [summa ajorata]
                                      (let [pituus (tieverkko-kyselyt/laske-tien-osien-pituudet (conj [] ajorata) suolarajoitus)
                                            summa (+ summa (:pituus pituus))]
                                        summa))
                              0 ajoratojen-pituudet)
          ;; Haetaan pohjavesialueet annetun tierekisterin perusteella
          pohjavesialueet (suolarajoitus-kyselyt/hae-leikkaavat-pohjavesialueet-tierekisterille db (select-keys suolarajoitus [:tie :aosa :aet :losa :let]))]
      {:pituus (:pituus pituus)
       :ajoratojen_pituus ajoratojen-pituus
       :pohjavesialueet pohjavesialueet})
    (transit-vastaus 400 {:virhe "Tierekisteriosoitteessa virhe."})))

(defn hae-talvisuolan-kayttorajat
  "Talvisuolan käyttöraja tulee urakka_tehtavamaara tauluun tallennetun suolauksen määrästä. Sanktiot ja indeksi tulevat suolasakko taulusta"
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (let [_ (log/debug "hae-talvisuolan-kayttoraja :: tiedot" tiedot)
        kokonaismaara (first (suolarajoitus-kyselyt/hae-talvisuolan-kokonaiskayttoraja db
                               {:urakka-id urakka-id
                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))

        talvisuolan-sanktiot (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db
                                      {:urakka-id urakka-id
                                       :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
        ;; Lisää kokonaismäärä myös talvisuolan-sanktioihin
        talvisuolan-sanktiot (assoc talvisuolan-sanktiot :kokonaismaara (:talvisuolan_kayttoraja kokonaismaara))
        rajoitusalueiden-suolasanktio (first (suolarajoitus-kyselyt/hae-rajoitusalueiden-suolasanktio db {:urakka-id urakka-id
                                                                                                          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
    {:talvisuolan-kokonaismaara (:talvisuolan_kayttoraja kokonaismaara)
     :talvisuolan-sanktiot talvisuolan-sanktiot
     ;; Palautetaan tyhjä map, jos mitään ei ole asetettu kantaan.
     :rajoitusalueiden-suolasanktio (if (nil? rajoitusalueiden-suolasanktio)
                                      {}
                                      rajoitusalueiden-suolasanktio)}))

(defn tallenna-talvisuolan-kayttoraja
  "Funktio ei nimestään huolimatta tallenna talvisuolan käyttörajoja, koska ne tallennetaan Tehtävät ja määrät sivulla.
  Tässä tallennetaan kokonaismäärälle sanktiot ja indeksi.
  Käyttöraja mäpin sisältö:
  {:urakka-id <id>
   :hoitokauden-alkuvuosi <vuosi>
   :indeksi <indeksin nimi>
   :sanktio_ylittavalta_tonnilta <maara>
   :kopioidaan-tuleville-vuosille? <true/false>}"
  [db user {:keys [urakka-id hoitokauden-alkuvuosi] :as kayttoraja}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (let [_ (log/debug "tallenna-talvisuolan-kayttoraja :: kayttoraja" kayttoraja)
        kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? kayttoraja)
        urakan-hoitovuodet (tulevat-hoitovuodet hoitokauden-alkuvuosi kopioidaan-tuleville-vuosille?
                             (first (urakat-kyselyt/hae-urakka db {:id urakka-id})))
        ;; Päivitä tiedot tai tallenna uusi
        vastaus
        (do
          (mapv (fn [vuosi]
                  (let [;; Hae käyttöraja kannasta, jos siellä olisi jo olemassa jotain pohjaa
                        hoitovuoden-kayttoraja (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db
                                                        {:urakka-id urakka-id
                                                         :hoitokauden-alkuvuosi vuosi}))
                        ;; Jätetaan mahdolliset id yms, tiedot jäljelle, jos tietokannassa oli jo olemassa jotain
                        hoitovuoden-kayttoraja (-> hoitovuoden-kayttoraja
                                                 (assoc :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta kayttoraja))
                                                 (assoc :indeksi (:indeksi kayttoraja))
                                                 (assoc :kayttaja-id (:id user))
                                                 (assoc :urakka-id urakka-id)
                                                 (assoc :hoitokauden-alkuvuosi vuosi)
                                                 (assoc :kaytossa true)) ;; Mahdollistetaan sanktioiden kääntäminen pois päältä

                        _ (if (:id hoitovuoden-kayttoraja)
                            (suolarajoitus-kyselyt/paivita-talvisuolan-kayttoraja! db hoitovuoden-kayttoraja)
                            (suolarajoitus-kyselyt/tallenna-talvisuolan-kayttoraja! db hoitovuoden-kayttoraja))]))
            urakan-hoitovuodet)
          (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db {:urakka-id urakka-id
                                                                     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))

        ;; Lisää vielä kokonaismäärä vastaukseen
        kokonaismaara (first (suolarajoitus-kyselyt/hae-talvisuolan-kokonaiskayttoraja db
                               {:urakka-id urakka-id
                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
        vastaus (assoc vastaus :kokonaismaara (:talvisuolan_kayttoraja kokonaismaara))]
    vastaus))


(defn tallenna-rajoitusalueen-sanktio [db user {:keys [urakka-id hoitokauden-alkuvuosi kopioidaan-tuleville-vuosille?] :as sanktio}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (let [_ (log/debug "tallenna-rajoitusalueen-sanktio :: sanktio" sanktio)
        urakan-hoitovuodet (tulevat-hoitovuodet hoitokauden-alkuvuosi kopioidaan-tuleville-vuosille?
                             (first (urakat-kyselyt/hae-urakka db {:id urakka-id})))
        ;; Päivitä tiedot tai luo uusi

        _ (mapv (fn [vuosi]
                  (let [;; Haetaan mahdollinen sanktio kannasta
                        hoitovuoden-rajoitusalue-sanktio (first (suolarajoitus-kyselyt/hae-rajoitusalueiden-suolasanktio db
                                                                  {:urakka-id urakka-id
                                                                   :hoitokauden-alkuvuosi vuosi}))
                        hoitovuoden-rajoitusalue-sanktio (-> hoitovuoden-rajoitusalue-sanktio
                                                           (assoc :sanktio_ylittavalta_tonnilta (:sanktio_ylittavalta_tonnilta sanktio))
                                                           (assoc :indeksi (:indeksi sanktio))
                                                           (assoc :kayttaja-id (:id user))
                                                           (assoc :urakka-id urakka-id)
                                                           (assoc :hoitokauden-alkuvuosi vuosi)
                                                           (assoc :kaytossa true))
                        _ (if (:id hoitovuoden-rajoitusalue-sanktio)
                            (suolarajoitus-kyselyt/paivita-rajoitusalueen-suolasanktio! db hoitovuoden-rajoitusalue-sanktio)
                            (suolarajoitus-kyselyt/tallenna-rajoitusalueen-suolasanktio! db hoitovuoden-rajoitusalue-sanktio))]))
            urakan-hoitovuodet)
        vastaus (first (suolarajoitus-kyselyt/hae-rajoitusalueiden-suolasanktio db {:urakka-id urakka-id
                                                                                    :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
    vastaus))

(defrecord Suolarajoitus []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-suolarajoitukset
      (fn [user tiedot]
        (hae-suolarajoitukset (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-suolarajoitus
      (fn [user tiedot]
        (tallenna-suolarajoitus (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :poista-suolarajoitus
      (fn [user tiedot]
        (poista-suolarajoitus (:db this) user tiedot)))

    ;; Tierekisteriosoitteen perusteella lasketaan ajoratojen pituus, reitin pituus sekä päätellään pohjavesialue/alueet
    (julkaise-palvelu (:http-palvelin this)
      :tierekisterin-tiedot
      (fn [user tiedot]
        (tierekisterin-tiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-talvisuolan-kayttorajat
      (fn [user tiedot]
        (hae-talvisuolan-kayttorajat (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-talvisuolan-kayttoraja
      (fn [user tiedot]
        (tallenna-talvisuolan-kayttoraja (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-rajoitusalueen-sanktio
      (fn [user tiedot]
        (tallenna-rajoitusalueen-sanktio (:db this) user tiedot)))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-suolarajoitukset
      :tallenna-suolarajoitus
      :poista-suolarajoitus
      :tierekisterin-tiedot
      :hae-talvisuolan-kayttorajat
      :tallenna-talvisuolan-kayttoraja
      :tallenna-rajoitusalueen-sanktio)
    this))
