(ns harja.palvelin.palvelut.suunnittelu.suolarajoitus-palvelu
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.tyokalut.yleiset :as yleiset-tyokalut]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.tyokalut.big :as big]))

(defn pituuden-laskennan-data-validi?
  [{:keys [tie aosa losa aet let] :as suolarajoitus}]
  (and
    (pos-int? tie)
    (int? aosa)
    (int? losa)
    (int? aet)
    (int? let)))

(defn- tierekisterin-tiedot [db {:keys [urakka-id hoitokauden-alkuvuosi] :as suolarajoitus}]
  (log/debug "tierekisterin-tiedot :: suolarajoitus: " suolarajoitus)
  (let [validaatiovirheet nil
        tierekisteri-valid? (pituuden-laskennan-data-validi? suolarajoitus)
        validaatiovirheet (if-not tierekisteri-valid?
                            (str validaatiovirheet "Tierekisteriosoitteessa virhe. \n")
                            validaatiovirheet)
        tarkistettava-suolarajoitus {:tie (:tie suolarajoitus)
                                     :aosa (:aosa suolarajoitus)
                                     :aet (:aet suolarajoitus)
                                     :losa (:losa suolarajoitus)
                                     :let (:let suolarajoitus)
                                     :urakka-id urakka-id
                                     :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                     :rajoitusalue-id (:rajoitusalue-id suolarajoitus)}
        paallekaiset (when tierekisteri-valid?
                       (suolarajoitus-kyselyt/onko-tierekisteriosoite-paallekainen db tarkistettava-suolarajoitus))
        validaatiovirheet (if-not (empty? paallekaiset)
                            (str validaatiovirheet " Tierekisteriosoitteessa on jo rajoitus. ")
                            validaatiovirheet)

        ;; Pilkotaan tierekisteri osiin tien osien mukaan
        tie-osien-pituudet (when tierekisteri-valid?
                             (tieverkko-kyselyt/hae-osien-pituudet db {:tie (:tie suolarajoitus)
                                                                       :aosa (:aosa suolarajoitus)
                                                                       :losa (:losa suolarajoitus)}))
        pituus (when tierekisteri-valid?
                 (tieverkko-kyselyt/laske-tien-osien-pituudet tie-osien-pituudet suolarajoitus))
        ajoratojen-pituudet (when tierekisteri-valid?
                              (tieverkko-kyselyt/hae-ajoratojen-pituudet db {:tie (:tie suolarajoitus)
                                                                             :aosa (:aosa suolarajoitus)
                                                                             :losa (:losa suolarajoitus)}))
        yhdistetyt-ajoradat2 (mapv (fn [[osa data]]
                                     (let [ajoratatiedot {:osa osa ;; Osa talteen
                                                          :pituus (or (some #(when (and (= (:ajorata %) 0) (= (:osa %) osa)) (:pituus %)) ajoratojen-pituudet) 0) ;; Ensimmäisen ajoradan pituus talteen
                                                          :ajoratojen-pituus (some #(when (and (= (:ajorata %) 1) (= (:osa %) osa)) (:pituus %)) ajoratojen-pituudet) ;; Oletetaan, että kaikki loput ajoradat ovat saman mittaisia
                                                          :ajoratojen-maara (count (keep #(when (and (not= (:ajorata %) 0) (= (:osa %) osa)) %) ajoratojen-pituudet)) ;; Määritellään ajoratojen määrä (yleensa ajoratoja on 0,1,2) joten tähän tulisi arvo 2
                                                          }
                                           ;; Lasketaan vielä yhteen kokonaispituus, koska siitä pitää päätellä paljon asioita laskennassa
                                           ajoratatiedot (assoc ajoratatiedot :kokonaispituus (+ (:pituus ajoratatiedot) (or (:ajoratojen-pituus ajoratatiedot) 0)))]
                                       ajoratatiedot))
                               ;; Yhdistaä mahdolliset ajoradata samaan mäppiin
                               (group-by :osa ajoratojen-pituudet))
        ajoratojen-pituus (reduce (fn [summa ajorata]
                                    (let [pituus (tieverkko-kyselyt/laske-tien-osien-pituudet (conj [] ajorata) suolarajoitus)
                                          summa (+ summa (:pituus pituus))]
                                      summa))
                            0 yhdistetyt-ajoradat2)
        ;; Haetaan pohjavesialueet annetun tierekisterin perusteella
        pohjavesialueet (when tierekisteri-valid?
                          (suolarajoitus-kyselyt/hae-leikkaavat-pohjavesialueet-tierekisterille db (select-keys suolarajoitus [:tie :aosa :aet :losa :let])))]
    ;; Palautetaan joko virheet, tai saadut tiedot
    (if validaatiovirheet
      {:validaatiovirheet validaatiovirheet}
      {:pituus (:pituus pituus)
       :ajoratojen_pituus ajoratojen-pituus
       :pohjavesialueet pohjavesialueet})))

(defn hae-tierekisterin-tiedot [db user {:keys [urakka-id] :as suolarajoitus}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka-id)
  (let [tr-tiedot (tierekisterin-tiedot db suolarajoitus)]
    (if (:validaatiovirheet tr-tiedot)
      (transit-vastaus 400 (:validaatiovirheet tr-tiedot))
      tr-tiedot)))

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

(defn hae-suolarajoitukset [db user {:keys [urakka_id hoitokauden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-suola user urakka_id)
  (let [_ (log/debug "Suolarajoitus :: hae-suolarajoitukset :: tiedot " tiedot)
        rajoitukset (suolarajoitus-kyselyt/hae-suolarajoitukset-hoitokaudelle db
                      {:hoitokauden-alkuvuosi hoitokauden-alkuvuosi
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
  (let [tr-tiedot (tierekisterin-tiedot db suolarajoitus)]
    (if (:validaatiovirheet tr-tiedot)
      (transit-vastaus 400 (:validaatiovirheet tr-tiedot))
      (let [_ (log/debug "tallenna-suolarajoitus :: tierekisteritiedot on validit" tr-tiedot)
            kopioidaan-tuleville-vuosille? (:kopioidaan-tuleville-vuosille? suolarajoitus)
            urakan-tiedot (first (urakat-kyselyt/hae-urakka db {:id (:urakka_id suolarajoitus)}))
            urakan-hoitovuodet (tulevat-hoitovuodet
                                 (:hoitokauden-alkuvuosi suolarajoitus)
                                 kopioidaan-tuleville-vuosille?
                                 urakan-tiedot)
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
            _ (if (:id db-rajoitus)
                (doseq [vuosi urakan-hoitovuodet]
                  (let [;; Haetaan päivitettävä rajoitus tietokannasta urakan ja vuoden perusteella. Meillä ei ole id:tä kaikille vuosille
                        haettu-rajoitus (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                                            :hoitokauden-alkuvuosi vuosi}))
                        rajoitus {:id (:rajoitus_id haettu-rajoitus)
                                  :kayttaja_id (:id user)
                                  :suolarajoitus (:suolarajoitus suolarajoitus)
                                  :hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi haettu-rajoitus)
                                  :formiaatti (:formiaatti suolarajoitus)
                                  :rajoitusalue_id (:rajoitusalue_id haettu-rajoitus)}
                        _ (suolarajoitus-kyselyt/paivita-suolarajoitus! db rajoitus)]
                    rajoitus))
                (doseq [vuosi urakan-hoitovuodet]
                  (let [rajoitus (assoc db-rajoitus :hoitokauden-alkuvuosi vuosi)
                        r (suolarajoitus-kyselyt/tallenna-suolarajoitus<! db (dissoc rajoitus :id))]
                    rajoitus)))
            suolarajoitus (first (suolarajoitus-kyselyt/hae-suolarajoitus db {:rajoitusalue_id (:id rajoitusalue)
                                                                              :hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi suolarajoitus)}))

            suolarajoitus (update suolarajoitus :pohjavesialueet
                            (fn [alueet]
                              (mapv
                                #(konv/pgobject->map % :tunnus :string :nimi :string)
                                (konv/pgarray->vector alueet))))]
        suolarajoitus))))

(defn poista-suolarajoitus [db user {:keys [kopioidaan-tuleville-vuosille? urakka_id rajoitusalue_id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-suola user urakka_id)
  (let [_ (log/debug "Suolarajoitus :: poista-suolarajoitus :: rajoitusalue_id:" rajoitusalue_id " hoitokauden-alkuvuosi: " hoitokauden-alkuvuosi)
        ;; Poistaa kaikki suolarajoitukset joilla on sama tai suurempi hoitokauden alkuvuosi, mikäli kopiointi on käytössä
        poistetut-rajoitukset (suolarajoitus-kyselyt/poista-suolarajoitus<! db (merge {:rajoitusalue_id rajoitusalue_id
                                                                                       :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
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
        _ (doseq [vuosi urakan-hoitovuodet]
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
        vastaus (first (suolarajoitus-kyselyt/hae-talvisuolan-sanktiot db {:urakka-id urakka-id
                                                                           :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))

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

        _ (doseq [vuosi urakan-hoitovuodet]
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
        vastaus (first (suolarajoitus-kyselyt/hae-rajoitusalueiden-suolasanktio db {:urakka-id urakka-id
                                                                                    :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))]
    vastaus))

(defn hae-suolatoteumat-rajoitusalueelle [db user {:keys [hoitokauden-alkuvuosi alkupvm loppupvm urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (let [_ (log/debug "hae-suolatoteumat-rajoitusalueelle :: tiedot" (pr-str tiedot))
        ;; Hae formiaatti ja talvisuolan materiaalityyppien id:t, jotta niiden summatiedot on helpompi laskea toteumista
        materiaalit (materiaalit-kyselyt/hae-talvisuolauksen-materiaalit db)
        talvisuolaidt (keep #(when (= "talvisuola" (:materiaalityyppi %))
                               (:id %))
                        materiaalit)
        suolatoteumat (suolarajoitus-kyselyt/hae-rajoitusalueet-summatiedoin db
                        {:urakka-id urakka-id
                         :alkupvm alkupvm
                         :loppupvm loppupvm
                         :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        suolatoteumat (mapv (fn [rivi]
                              (-> rivi
                                (update :pohjavesialueet
                                  (fn [alueet]
                                    (mapv
                                      #(konv/pgobject->map % :tunnus :string :nimi :string)
                                      (konv/pgarray->vector alueet))))))
                        suolatoteumat)

        _ (log/debug "suolatoteumat" suolatoteumat)
        suolatoteumat (mapv (fn [rivi]
                              (cond-> rivi
                                true (konv/decimal->double rivi :suolatoteumat :formiaattitoteumat :ajoratojen_pituus)
                                (and
                                  (not (nil? (:formiaattitoteumat rivi)))
                                  (not (nil? (:ajoratojen_pituus rivi)))
                                  (> (:formiaattitoteumat rivi) 0)
                                  (> (:ajoratojen_pituus rivi) 0))
                                (assoc :formiaatit_t_per_ajoratakm
                                       (with-precision 3 (/ (:formiaattitoteumat rivi) (:ajoratojen_pituus rivi))))
                                (and
                                  (not (nil? (:suolatoteumat rivi)))
                                  (not (nil? (:ajoratojen_pituus rivi)))
                                  (> (:suolatoteumat rivi) 0)
                                  (> (:ajoratojen_pituus rivi) 0))
                                (assoc :talvisuola_t_per_ajoratakm
                                       (with-precision 4 (/ (:suolatoteumat rivi) (:ajoratojen_pituus rivi))))))
                        suolatoteumat)]
    suolatoteumat))

(defn hae-rajoitusalueen-summatiedot
  "Haetaan päivittäin groupatut suolatoteumat halutulle rajoitusalueelle"
  [db user {:keys [rajoitusalue-id alkupvm loppupvm urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (let [_ (log/debug "hae-rajoitusalueen-summatiedot :: tiedot" (pr-str tiedot))
        suolatoteumat (suolarajoitus-kyselyt/hae-rajoitusalueen-suolatoteumasummat db
                        {:urakka-id urakka-id
                         :rajoitusalue-id rajoitusalue-id
                         :alkupvm alkupvm
                         :loppupvm loppupvm})
        suolatoteumat (mapv (fn [rivi]
                              (-> rivi
                                (assoc :maara (or (:formiaattimaara rivi) (:suolamaara rivi)))
                                (assoc :lukumaara (or (:formiaattilukumaara rivi) (:suolalukumaara rivi)))
                                (assoc :rivi-id (hash rivi))))
                        suolatoteumat)]
    suolatoteumat))

(defn hae-rajoitusalueen-paivan-toteumat
  "Haetaan yhden päivän toteumat rajoitusalueelle materiaali-id:n perusteella"
  [db user {:keys [rajoitusalue-id pvm materiaali-id urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user urakka-id)
  (let [_ (log/debug "hae-rajoitusalueen-paivan-toteumat :: tiedot" (pr-str tiedot))
        paivan-toteumat (suolarajoitus-kyselyt/hae-rajoitusalueen-paivan-toteumat db
                          {:urakka-id urakka-id
                           :rajoitusalue-id rajoitusalue-id
                           :materiaali-id materiaali-id
                           :alkupvm (c/to-sql-time pvm)
                           :loppupvm (c/to-sql-time (pvm/ajan-muokkaus (pvm/joda-timeksi pvm) true 1 :paiva))})
        paivan-toteumat (mapv #(assoc % :maara (or (:formiaattimaara %) (:suolamaara %))) paivan-toteumat)]
    paivan-toteumat))

(defn hae-pohjavesialueidenurakat [db user tiedot]
  ;;TODO: Varmista oikeat käyttöoikeudet
  (log/debug "hae-pohjavesialueidenurakat :: urakat" hae-pohjavesialueidenurakat)
  (let [urakat (suolarajoitus-kyselyt/hae-pohjavesialueidenurakat db)]
    urakat))

(defn hae-urakan-siirrettavat-pohjavesialueet [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-suola user (:urakkaid tiedot))
  (log/debug "hae-urakan-siirrettavat-pohjavesialueet :: tiedot" tiedot)
  (let [;; Haetaan urakan tiedot ja tarvittaessa luodaan pohjavesialue_talvisuola riveistä jokaiselle vuodelle oma instanssi
        urakkatiedot (first (urakat-kyselyt/hae-urakka db {:id (:urakkaid tiedot)}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakkatiedot))
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakkatiedot))
        urakan-vuodet (range urakan-alkuvuosi urakan-loppuvuosi)
        alueet (suolarajoitus-kyselyt/hae-urakan-siirrettavat-pohjavesialueet db {:urakkaid (:urakkaid tiedot)})
        ;; Lisää pituudet alueille
        alueet (map (fn [alue]
                      (let [tierekisterin-tiedot (tierekisterin-tiedot db alue)]
                        (-> alue
                          (assoc :ajoratojen_pituus (:ajoratojen_pituus tierekisterin-tiedot))
                          (assoc :pituus (:pituus tierekisterin-tiedot)))))
                 alueet)]
    alueet))

(defn siirra-urakan-pohjavesialueet [db user tiedot]
  (let [_ (log/debug "siirra-urakan-pohjavesialueet :: tiedot" tiedot)
        urakkaid (:urakkaid tiedot)
        urakkatiedot (first (urakat-kyselyt/hae-urakka db {:id urakkaid}))
        urakan-loppuvuosi (pvm/vuosi (:loppupvm urakkatiedot))
        urakan-pohjavesialueet (:pohjavesialueet tiedot)
        _ (doseq [pohjavesialue urakan-pohjavesialueet]
            (let [pohjavesialueen-vuosi (:hoitokauden-alkuvuosi pohjavesialue)

                  tallennettava-suolarajoitus {:hoitokauden-alkuvuosi pohjavesialueen-vuosi
                                               :kopioidaan-tuleville-vuosille? true
                                               :urakka_id urakkaid
                                               :tie (:tie pohjavesialue)
                                               :aosa (:aosa pohjavesialue)
                                               :aet (:aet pohjavesialue)
                                               :losa (:losa pohjavesialue)
                                               :let (:let pohjavesialue)
                                               :suolarajoitus (:talvisuolaraja pohjavesialue)
                                               :formiaatti (if (= 0 (:talvisuolaraja pohjavesialue)) true false)
                                               :kayttaja_id (:luoja pohjavesialue)}
                  tr-tiedot (tierekisterin-tiedot db tallennettava-suolarajoitus)
                  tallennettava-suolarajoitus (-> tallennettava-suolarajoitus
                                                (assoc :pituus (:pituus tr-tiedot))
                                                (assoc :ajoratojen_pituus (:ajoratojen_pituus tr-tiedot)))]
              ;; Tallennetaan ensin rajoitusalue uutena tai päivityksenä
              (tallenna-suolarajoitus db user tallennettava-suolarajoitus)))]))

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
        (hae-tierekisterin-tiedot (:db this) user tiedot)))

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

    (julkaise-palvelu (:http-palvelin this)
      :hae-suolatoteumat-rajoitusalueelle
      (fn [user tiedot]
        (hae-suolatoteumat-rajoitusalueelle (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-rajoitusalueen-summatiedot
      (fn [user tiedot]
        (hae-rajoitusalueen-summatiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-rajoitusalueen-paivan-toteumat
      (fn [user tiedot]
        (hae-rajoitusalueen-paivan-toteumat (:db this) user tiedot)))

    ;; Käytetään lyhyen aikaa hallintapuolelta, jotta rajoitusalueet saadaan muodostettua pohjavesialueiden perusteella
    (julkaise-palvelu (:http-palvelin this)
      :hae-pohjavesialueurakat
      (fn [user tiedot]
        (hae-pohjavesialueidenurakat (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :hae-urakan-siirrettavat-pohjavesialueet
      (fn [user tiedot]
        (hae-urakan-siirrettavat-pohjavesialueet (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :siirra-urakan-pohjavesialueet
      (fn [user tiedot]
        (siirra-urakan-pohjavesialueet (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-suolarajoitukset
      :tallenna-suolarajoitus
      :poista-suolarajoitus
      :tierekisterin-tiedot
      :hae-talvisuolan-kayttorajat
      :tallenna-talvisuolan-kayttoraja
      :tallenna-rajoitusalueen-sanktio
      :hae-suolatoteumat-rajoitusalueelle
      :hae-rajoitusalueen-summatiedot
      :hae-rajoitusalueen-paivan-toteumat
      :hae-pohjavesialueurakat
      :hae-urakan-siirrettavat-pohjavesialueet
      :siirra-urakan-pohjavesialueet)
    this))
