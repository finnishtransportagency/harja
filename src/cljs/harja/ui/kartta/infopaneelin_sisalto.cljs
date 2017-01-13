(ns harja.ui.kartta.infopaneelin-sisalto
  "Määrittelee erilaisten kartalle piirrettävien asioiden tiedot, jotka tulevat kartan
  infopaneeliin.
  Infopaneelissa voidaan näyttää erityyppisiä asioita, samalla tavalla kuin kartalle
  piirtämisessäkin. Jokaiselle eri tyyppiselle asialle täytyy määritellä toteutus
  infopaneli-skeema multimetodiin.

  Infopaneeli-skeema palauttaa mäp muotoisen kuvauksen näytettävästä asiasta, jossa
  on seuraavat avaimet:

  :tyyppi       näytettävän asian tyyppi keyword
  :jarjesta-fn  funktio joka palauttaa datasta sort avaimen (pvm)
  :otsikko      asialle näytettävä otsikko
  :tiedot       asialle näytettävien tietokenttien skeemat
  :data         itse näytettävä asia, josta tietokenttien arvot saadaan
  "
  (:require [clojure.string :as string]
            [harja.pvm :as pvm]
            [harja.loki :as log :refer [log]]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.turvallisuuspoikkeamat :as turpodomain]
            [harja.domain.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.fmt :as fmt]
            [clojure.string :as str]))

(defmulti infopaneeli-skeema :tyyppi-kartalla)

(defmethod infopaneeli-skeema :tyokone [tyokone]
  {:tyyppi :tyokone
   :jarjesta-fn :alkanut
   :otsikko (str "Työkone: "
                 (when (:tehtavat tyokone)
                   (string/join ", " (:tehtavat tyokone)))
                 " " (pvm/pvm-aika (:alkanut tyokone)))
   :tiedot [{:otsikko "Työ aloitettu" :tyyppi :pvm-aika :nimi :alkanut}
            {:otsikko "Viimeisin havainto" :tyyppi :pvm-aika :nimi :lahetysaika}
            {:otsikko "Tyyppi" :tyyppi :string :nimi :tyokonetyyppi}
            {:otsikko "Organisaatio" :tyyppi :string :hae #(or (:organisaationimi %) "Ei organisaatiotietoja")}
            {:otsikko "Urakka" :tyyppi :string :hae #(or (:urakkanimi %) "Ei urakkatietoja")}
            {:otsikko "Tehtävät" :tyyppi :string
             :hae #(string/join ", " (:tehtavat %))}]
   :data tyokone})

(defn ilmoituksen-tiedot [ilmoitus]
  {:tyyppi :ilmoitus
   :jarjesta-fn :ilmoitettu
   :otsikko (str (condp = (:ilmoitustyyppi ilmoitus)
                   :toimenpidepyynto "Toimenpidepyyntö"
                   :tiedoitus "Tiedotus"
                   (string/capitalize (name (:ilmoitustyyppi ilmoitus))))
                 " " (pvm/pvm-aika (:ilmoitettu ilmoitus)))
   :tiedot [{:otsikko "Id" :tyyppi :string :nimi :ilmoitusid}
            {:otsikko "Ilmoitettu" :tyyppi :pvm-aika :nimi :ilmoitettu}
            {:otsikko "Otsikko" :tyyppi :string :nimi :otsikko}
            {:otsikko "Paikan kuvaus" :tyyppi :string :nimi :paikankuvaus}
            {:otsikko "Lisätietoja" :tyyppi :string :nimi :lisatieto}
            {:otsikko "Kuittaukset" :tyyppi :positiivinen-numero
             :hae #(count (:kuittaukset ilmoitus))}]
   :data ilmoitus})

(defmethod infopaneeli-skeema :toimenpidepyynto [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))
(defmethod infopaneeli-skeema :tiedoitus [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))
(defmethod infopaneeli-skeema :kysely [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))


(defmethod infopaneeli-skeema :varustetoteuma [toteuma]
  {:tyyppi :varustetoteuma
   :jarjesta-fn :alkupvm
   :otsikko (str "Varustetoteuma " (pvm/pvm-aika (:alkupvm toteuma)))
   :tiedot [{:otsikko "Päivämäärä" :tyyppi :pvm :nimi :alkupvm}
            {:otsikko "Tunniste" :tyyppi :string :nimi :tunniste}
            {:otsikko "Tietolaji" :tyyppi :string :nimi :tietolaji}
            {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
            {:otsikko "Kuntoluokka" :tyyppi :string :nimi :kuntoluokka}
            {:otsikko "Avaa varustekortti" :tyyppi :linkki :nimi :varustekortti-url}]
   :data toteuma})

(defn- yllapitokohde-skeema
  "Ottaa ylläpitokohdeosan, jolla on lisäksi tietoa sen 'pääkohteesta' :yllapitokohde avaimen takana."
  [yllapitokohdeosa]
  (let [aloitus :kohde-alkupvm
        paallystys-valmis :paallystys-loppupvm
        paikkaus-valmis :paikkaus-loppupvm
        kohde-valmis :kohde-valmispvm]
    {:tyyppi (:yllapitokohdetyotyyppi (:yllapitokohde yllapitokohdeosa))
     :jarjesta-fn :kohde-alkupvm
     :otsikko (case (:yllapitokohdetyotyyppi (:yllapitokohde yllapitokohdeosa))
                :paallystys "Päällystyskohde"
                :paikkaus "Paikkauskohde"
                :default nil)
     :tiedot [{:otsikko "Kohde" :tyyppi :string :hae #(get-in % [:yllapitokohde :nimi])}
              {:otsikko "Kohdenumero" :tyyppi :string :hae #(get-in % [:yllapitokohde :kohdenumero])}
              {:otsikko "Kohteen osoite" :tyyppi :string
               :hae #(tr-domain/tierekisteriosoite-tekstina (:yllapitokohde %))}
              {:otsikko "Kohteen pituus (m)" :tyyppi :string
               :hae #(fmt/desimaaliluku-opt (get-in % [:yllapitokohde :pituus]) 0)}
              {:otsikko "Alikohde" :tyyppi :string
               :hae #(:nimi %)}
              {:otsikko "Alikohteen osoite" :tyyppi :string
               :hae #(tr-domain/tierekisteriosoite-tekstina %)}
              {:otsikko "Nykyinen päällyste" :tyyppi :string
               :hae #(paallystys-ja-paikkaus/hae-paallyste-koodilla (get-in % [:yllapitokohde :nykyinen-paallyste]))}
              {:otsikko "KVL" :tyyppi :string
               :hae #(fmt/desimaaliluku-opt (get-in % [:yllapitokohde :keskimaarainen-vuorokausiliikenne]) 0)}
              {:otsikko "Toimenpide" :tyyppi :string
               :hae #(:toimenpide %)}
              {:otsikko "Tila" :tyyppi :string
               :hae #(yllapitokohteet/kuvaile-kohteen-tila (get-in % [:yllapitokohde :tila]))}
              (when (get-in yllapitokohdeosa [:yllapitokohde aloitus])
                {:otsikko "Aloitettu" :tyyppi :pvm-aika
                 :hae #(get-in % [:yllapitokohde aloitus])})
              (when (get-in yllapitokohdeosa [:yllapitokohde paallystys-valmis])
                {:otsikko "Päällystys valmistunut" :tyyppi :pvm-aika
                 :hae #(get-in % [:yllapitokohde paallystys-valmis])})
              (when (get-in yllapitokohdeosa [:yllapitokohde paikkaus-valmis])
                {:otsikko "Paikkaus valmistunut" :tyyppi :pvm-aika
                 :hae #(get-in % [:yllapitokohde paikkaus-valmis])})
              (when (get-in yllapitokohdeosa [:yllapitokohde kohde-valmis])
                {:otsikko "Kohde valmistunut" :tyyppi :pvm-aika
                 :hae #(get-in % [:yllapitokohde kohde-valmis])})
              {:otsikko "Urakka" :tyyppi :string :hae #(get-in % [:yllapitokohde :urakka])}
              {:otsikko "Urakoitsija" :tyyppi :string :hae #(get-in % [:yllapitokohde :urakoitsija])}]
     :data yllapitokohdeosa}))

(defmethod infopaneeli-skeema :paallystys [paallystys]
  (yllapitokohde-skeema paallystys))

(defmethod infopaneeli-skeema :paikkaus [paikkaus]
  (yllapitokohde-skeema paikkaus))

(defmethod infopaneeli-skeema :turvallisuuspoikkeama [turpo]
  (let [tapahtunut :tapahtunut
        paattynyt :paattynyt
        kasitelty :kasitelty]
    {:tyyppi :turvallisuuspoikkeama
     :jarjesta-fn :tapahtunut
     :otsikko (str "Turvallisuuspoikkeama " (pvm/pvm-aika (tapahtunut turpo)))
     :tiedot [(when (tapahtunut turpo)
                {:otsikko "Tapahtunut" :tyyppi :pvm-aika :nimi tapahtunut})
              (when (kasitelty turpo)
                {:otsikko "Käsitelty" :tyyppi :pvm-aika :nimi kasitelty})
              {:otsikko "Työn\u00ADtekijä" :hae #(turpodomain/kuvaile-tyontekijan-ammatti %)}
              {:otsikko "Vammat" :hae #(turpodomain/vammat (:vammat %))}
              {:otsikko "Sairaala\u00ADvuorokaudet" :hae #(:sairaalavuorokaudet %)}
              {:otsikko "Sairaus\u00ADpoissaolo\u00ADpäivät" :tyyppi :positiivinen-numero :nimi :sairauspoissaolopaivat}
              {:otsikko "Vakavuus\u00ADaste" :hae #(turpodomain/turpo-vakavuusasteet (:vakavuusaste %))}
              {:otsikko "Kuvaus" :nimi :kuvaus}
              {:otsikko "Korjaavat toimen\u00ADpiteet"
               :hae #(str (count (filter :suoritettu (:korjaavattoimenpiteet %)))
                          "/"
                          (count (:korjaavattoimenpiteet %)))}]
     :data turpo}))

(defmethod infopaneeli-skeema :tarkastus [tarkastus]
  (let [havainnot-fn #(cond
                        (and (:havainnot %) (not-empty (:vakiohavainnot %)))
                        (str (:havainnot %) " & " (string/join ", " (:vakiohavainnot %)))

                        (:havainnot %)
                        (:havainnot %)

                        (not-empty (:vakiohavainnot %))
                        (string/join ", " (:vakiohavainnot %))

                        :default nil)]
    {:tyyppi :tarkastus
     :jarjesta-fn :aika
     :otsikko (str (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus))
                   (str " " (pvm/pvm-aika (:aika tarkastus))))
     :tiedot [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
              {:otsikko "Tierekisteriosoite" :tyyppi :tierekisteriosoite :nimi :tierekisteriosoite}
              {:otsikko "Tarkastaja" :nimi :tarkastaja}
              {:otsikko "Havainnot" :hae havainnot-fn}]
     :data tarkastus}))

(defmethod infopaneeli-skeema :laatupoikkeama [laatupoikkeama]
  (let [paatos #(get-in % [:paatos :paatos])
        kasittelyaika #(get-in % [:paatos :kasittelyaika])]
    {:tyyppi :laatupoikkeama
     :jarjesta-fn :aika
     :otsikko (str "Laatupoikkeama " (pvm/pvm-aika (:aika laatupoikkeama)))
     :tiedot [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
              {:otsikko "Tekijä" :hae #(str (:tekijanimi %) ", " (name (:tekija %)))}
              {:otsikko "Kuvaus" :nimi :kuvaus :tyyppi :string}
              {:otsikko "Tierekisteriosoite" :hae #(if-let [yllapitokohde-tie (get-in % [:yllapitokohde :tr])]
                                                     (tr-domain/tierekisteriosoite-tekstina
                                                       yllapitokohde-tie)
                                                     (tr-domain/tierekisteriosoite-tekstina
                                                       (:tr %)))}
              (when (:yllapitokohde laatupoikkeama)
                {:otsikko "Kohde" :hae #(let [yllapitokohde (:yllapitokohde %)]
                                          (str (:numero yllapitokohde)
                                               ", "
                                               (:nimi yllapitokohde)))})
              (when (and (paatos laatupoikkeama) (kasittelyaika laatupoikkeama))
                {:otsikko "Päätös"
                 :hae #(str (laatupoikkeamat/kuvaile-paatostyyppi (paatos %))
                            " (" (pvm/pvm-aika (kasittelyaika %)) ")")})]
     :data laatupoikkeama}))

(defmethod infopaneeli-skeema :suljettu-tieosuus [osuus]
  {:tyyppi :suljettu-tieosuus
   :jarjesta-fn :aika
   :otsikko "Suljettu tieosuus"
   :tiedot [{:otsikko "Ylläpitokohde" :hae #(str (:yllapitokohteen-nimi %) " (" (:yllapitokohteen-numero %) ")")}
            {:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
            {:otsikko "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
            {:otsikko "Kaistat" :hae #(string/join ", " (map str (:kaistat %)))}
            {:otsikko "Ajoradat" :hae #(string/join ", " (map str (:ajoradat %)))}]
   :data osuus})

(defmethod infopaneeli-skeema :toteuma [toteuma]
  {:tyyppi :toteuma
   :jarjesta-fn :alkanut
   :otsikko (let [toimenpiteet (map :toimenpide (:tehtavat toteuma))
                  _ (log "toteuma" (pr-str toteuma))]
              (str (if (empty? toimenpiteet)
                     "Toteuma"
                     (string/join ", " toimenpiteet))
                   (str " " (pvm/pvm-aika (:alkanut toteuma)))))
   :tiedot (vec (concat [{:otsikko "Alkanut" :tyyppi :pvm-aika :nimi :alkanut}
                         {:otsikko "Päättynyt" :tyyppi :pvm-aika :nimi :paattynyt}
                         {:otsikko "Tierekisteriosoite" :tyyppi :tierekisteriosoite
                          :nimi :tierekisteriosoite}
                         {:otsikko "Suorittaja" :hae #(get-in % [:suorittaja :nimi])}]

                        (for [{:keys [toimenpide maara yksikko]} (:tehtavat toteuma)]
                          {:otsikko toimenpide
                           :hae (constantly (str maara " " yksikko))})

                        (for [materiaalitoteuma (:materiaalit toteuma)]
                          {:otsikko (get-in materiaalitoteuma [:materiaali :nimi])
                           :hae #(str (get-in % [:materiaalit materiaalitoteuma :maara]) " "
                                      (get-in % [:materiaalit materiaalitoteuma :materiaali :yksikko]))})
                        (when (:lisatieto toteuma)
                          [{:otsikko "Lisätieto" :nimi :lisatieto}])))
   :data toteuma})


(defmethod infopaneeli-skeema :silta [silta]
  {:tyyppi :silta
   :jarjesta-fn :tarkastusaika
   :otsikko (str "Silta " (when (:tarkastusaika silta)
                            (pvm/pvm-aika (:tarkastusaika silta))))
   :tiedot [{:otsikko "Nimi" :hae :siltanimi}
            {:otsikko "Sillan tunnus" :hae :siltatunnus}
            {:otsikko "Edellinen tarkastus" :tyyppi :pvm :hae :tarkastusaika}
            {:otsikko "Edellinen tarkastaja" :hae :tarkastaja}]
   :data silta})

(defmethod infopaneeli-skeema :tietyomaa [tietyomaa]
  {:tyyppi :tietyomaa
   :jarjesta-fn :aika
   :otsikko (str "Tietyömaa " (when (:aika tietyomaa)
                                (pvm/pvm-aika (:aika tietyomaa))))
   :tiedot [{:otsikko "Ylläpitokohde" :hae #(str (:yllapitokohteen-nimi %) " (" (:yllapitokohteen-numero %) ")")}
            {:otsikko "Aika" :hae #(pvm/pvm-aika (:aika %))}
            {:otsikko "Osoite" :hae #(tr-domain/tierekisteriosoite-tekstina % {:teksti-tie? false})}
            {:otsikko "Kaistat" :hae #(clojure.string/join ", " (map str (:kaistat %)))}
            {:otsikko "Ajoradat" :hae #(clojure.string/join ", " (map str (:ajoradat %)))}
            {:otsikko "Nopeusrajoitus" :hae :nopeusrajoitus}]
   :data tietyomaa})

(defmethod infopaneeli-skeema :default [x]
  (log/warn "infopaneeli-skeema metodia ei implementoitu tyypille " (pr-str (:tyyppi-kartalla x))
            ", palautetaan tyhjä itemille " (pr-str x))
  nil)

(defn- rivin-skeemavirhe [viesti rivin-skeema infopaneeli-skeema]
  (do
    (log viesti
         ", rivin-skeema: " (clj->js rivin-skeema)
         ", infopaneeli-skeema: " (clj->js infopaneeli-skeema))
    nil))

(defn- validoi-rivin-skeema
  "Validoi rivin skeeman annetulle infopaneeli skeemalle. Palauttaa skeeman, jos se on validi.
  Jos skeema ei ole validi tiedolle, logittaa virheen ja palauttaa nil."
  [infopaneeli-skeema {:keys [nimi hae otsikko] :as rivin-skeema}]
  (let [data (:data infopaneeli-skeema)
        get-fn (or nimi hae)
        arvo (when get-fn
               (get-fn data))]
    (cond
      ;; Ei ole otsikkoa
      (nil? otsikko)
      (rivin-skeemavirhe "Rivin skeemasta puuttuu otsikko"
                         rivin-skeema infopaneeli-skeema)

      ;; Hakutapa puuttuu kokonaan
      (nil? get-fn)
      (rivin-skeemavirhe "skeemasta puuttuu :nimi tai :hae"
                         rivin-skeema infopaneeli-skeema)

      ;; Hakutapa on nimi, mutta datassa ei ole kyseistä avainta
      (and nimi (not (contains? data nimi)))
      (rivin-skeemavirhe
        (str "Tiedossa ei ole nimen mukaista avainta, nimi: "
             (str nimi))
        rivin-skeema infopaneeli-skeema)

      ;; Hakutapa on funktio, joka palautti nil arvon
      (nil? arvo)
      (rivin-skeemavirhe (str "Puuttuva tieto otsikolla " (:otsikko rivin-skeema))
                         rivin-skeema infopaneeli-skeema)

      ;; Kaikki kunnossa
      :default
      rivin-skeema)))

(defn validoi-infopaneeli-skeema
  "Validoi infopaneeli-skeema metodin muodostaman skeeman ja kaikki sen kentät.
  Jos skeema on validi, palauttaa skeeman sen valideilla kentillä.
  Jos skeema ei ole validi, logittaa virheen ja palauttaa nil."
  [{:keys [otsikko tiedot data jarjesta-fn] :as infopaneeli-skeema}]
  (let [validit-skeemat (vec (keep (partial validoi-rivin-skeema infopaneeli-skeema)
                                   tiedot))]
    (cond
      (nil? jarjesta-fn)
      (do (log (str "jarjesta-fn puuttuu tiedolta " (pr-str infopaneeli-skeema)))
          nil)

      (empty? validit-skeemat)
      (do (log (str "Tiedolla ei ole yhtään validia skeemaa: " (pr-str infopaneeli-skeema)))
          nil)

      :default
      (assoc infopaneeli-skeema :tiedot validit-skeemat))))

(defn skeemamuodossa [asiat]
  (->> asiat
       (keep infopaneeli-skeema)
       (keep validoi-infopaneeli-skeema)
       (sort-by (fn [{jarjesta-fn :jarjesta-fn data :data}]
                  (jarjesta-fn data)))
       vec))
