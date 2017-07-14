(ns harja.palvelin.palvelut.urakat
  (:require [com.stuartsierra.component :as component]
            [harja.domain.roolit :as roolit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.urakat :as q]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.domain.urakka :as u]
            [harja.domain.sopimus :as s]
            [harja.domain.hanke :as h]
            [harja.domain.organisaatio :as o]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.hankkeet :as hankkeet-palvelu]
            [namespacefy.core :refer [namespacefy]]
            [harja.kyselyt.laskutusyhteenveto :as laskutusyhteenveto-q]
            [harja.id :refer [id-olemassa?]]
            [harja.geo :refer [muunna-pg-tulokset]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as c]
            [harja.palvelin.integraatiot.sahke.sahke-komponentti :as sahke]))

(def ^{:const true} oletus-toleranssi 50)

(defn urakoiden-alueet
  [db user oikeus urakka-idt toleranssi]
  (when-not (empty? urakka-idt)
    (into []
          (comp
            (filter (fn [{:keys [urakka_id]}]
                      (oikeudet/voi-lukea? oikeus urakka_id user)))
            (harja.geo/muunna-pg-tulokset :urakka_alue)
            (harja.geo/muunna-pg-tulokset :alueurakka_alue)
            (map konv/alaviiva->rakenne))
          (q/hae-urakoiden-geometriat db (or toleranssi oletus-toleranssi) urakka-idt))))

(defn hae-urakka-idt-sijainnilla
  "Hakee annetun tyyppisen urakan sijainnilla. Mikäli tyyppiä vastaavaa urakkaa ei löydy, haetaan alueella toimiva
  hoidon alueurakka. Mikäli alueelta ei löydy alueurakkaa, haetaan lähin hoidon alueurakka"
  [db urakkatyyppi {:keys [x y]}]
  ;; Oletuksena haetaan valaistusurakat & päällystyksen palvelusopimukset 1000 metrin thesholdilla
  (let [urakka-idt (map :id (q/hae-urakka-sijainnilla db urakkatyyppi x y 1000))]
    (if (empty? urakka-idt)
      (if (= "hoito" urakkatyyppi)
        ;; Jos hoidon alueurakkaa ei löytynyt suoraan alueelta, haetaan lähin hoidon alueurakka 10 kilometrin säteellä
        (map :id (q/hae-lahin-hoidon-alueurakka db x y 10000))

        ;; Jos ei löytynyt urakkaa annetulla tyypillä, haetaan alueella toimiva hoidon alueurakka
        (let [hoidon-urakkaidt (map :id (q/hae-urakka-sijainnilla db "hoito" x y 10))]
          (if hoidon-urakkaidt
            hoidon-urakkaidt
            ;; Jos hoidon alueurakkaa ei löytynyt suoraan alueelta, haetaan lähin hoidon alueurakka 10 kilometrin säteellä
            (map :id (q/hae-lahin-hoidon-alueurakka db x y 10000)))))
      urakka-idt)))

(defn- pura-sopimukset [{jdbc-array :sopimukset :as urakka}]
  (loop [sopimukset {}
         paasopimus nil
         [s & ss] (when jdbc-array (seq (.getArray jdbc-array)))]
    (if-not s
      (assoc urakka
        :sopimukset sopimukset
        :paasopimus paasopimus)
      (let [[id sampoid] (str/split s #"=")
            paasopimus? (str/starts-with? id "*")
            id (Long/parseLong
                 (if paasopimus?
                   (subs id 1)
                   id))]
        (recur (assoc sopimukset
                 id sampoid)
               (if paasopimus?
                 id
                 paasopimus)
               ss)))))

(def urakka-xf
  (comp (muunna-pg-tulokset :alue :alueurakan_alue)

        ;; Aseta alue, jos se löytyy
        (map #(if-let [alueurakka (:alueurakan_alue %)]
                (-> %
                    (dissoc :alueurakan_alue)
                    (assoc :alue alueurakka))
                (dissoc % :alueurakan_alue)))

        (map #(assoc % :urakoitsija {:id (:urakoitsija_id %)
                                     :nimi (:urakoitsija_nimi %)
                                     :ytunnus (:urakoitsija_ytunnus %)}))

        (map #(assoc % :loppupvm (pvm/aikana (:loppupvm %) 23 59 59 999))) ; Automaattikonversiolla aika on 00:00

        (map #(assoc % :takuu {:loppupvm (:takuu_loppupvm %)}))

        ;; Sopimukset kannasta vectorina, jossa 1. elementti on id ja
        ;; 2. elementti on sopimuksen tekstikuvaus (sampoid tai nimi):
        ;; ["2=8H05228/01" "*3=8H05228/10"]
        ;; Pääsopimus on se, joka alkaa '*' merkillä.

        ;; Tarjotaan ulos muodossa {:sopimukset {"2" "8H05228/01", "3" "8H05228/10"
        ;;                          :paasopimus 3}
        (map pura-sopimukset)

        (map #(assoc % :hallintayksikko {:id (:hallintayksikko_id %)
                                         :nimi (:hallintayksikko_nimi %)
                                         :lyhenne (:hallintayksikko_lyhenne %)}))

        (map #(if-let [tyyppi (:tyyppi %)]
                ;; jos urakkatyypissä on välilyöntejä, korvataan ne väliviivalla, jotta muodostuu validi keyword
                (assoc % :tyyppi (keyword (str/replace (:tyyppi %) " " "-")))
                %))

        (map #(assoc % :sopimustyyppi (and (:sopimustyyppi %) (keyword (:sopimustyyppi %)))))

        ;; Käsitellään päällystysurakan tiedot

        (map #(konv/array->vec % :yha_elyt))
        (map #(konv/array->vec % :yha_vuodet))

        (map #(if (:yha_yhaid %)
                (assoc % :yhatiedot {:yhatunnus (:yha_yhatunnus %)
                                     :yhaid (:yha_yhaid %)
                                     :yhanimi (:yha_yhanimi %)
                                     :elyt (:yha_elyt %)
                                     :vuodet (:yha_vuodet %)
                                     :kohdeluettelo-paivitetty (:yha_kohdeluettelo_paivitetty %)
                                     :kohdeluettelo-paivittaja (:yha_kohdeluettelo_paivittaja %)
                                     :kohdeluettelo-paivittaja-etunimi (:yha_kohdeluettelo_paivittaja_etunimi %)
                                     :kohdeluettelo-paivittaja-sukunimi (:yha_kohdeluettelo_paivittaja_sukunimi %)
                                     :sidonta-lukittu? (:yha_sidonta_lukittu %)})
                %))

        ;; Poista käsitellyt avaimet

        (map #(dissoc %
                      :urakoitsija_id :urakoitsija_nimi :urakoitsija_ytunnus
                      :hallintayksikko_id :hallintayksikko_nimi :hallintayksikko_lyhenne
                      :yha_yhatunnus :yha_yhaid :yha_yhanimi :yha_elyt :yha_vuodet
                      :yha_kohdeluettelo_paivitetty :yha_sidonta_lukittu :takuu_loppupvm))))

(defn hallintayksikon-urakat [db {organisaatio :organisaatio :as user} hallintayksikko-id]
  (log/debug "Haetaan hallintayksikön urakat: " hallintayksikko-id)
  (if-not organisaatio
    (do
      (oikeudet/ei-oikeustarkistusta!)
      [])
    (let [urakat (oikeudet/kayttajan-urakat user)]
      (into []
            urakka-xf
            (q/listaa-urakat-hallintayksikolle db
                                               {:hallintayksikko hallintayksikko-id
                                                :kayttajan_org_id (:id organisaatio)
                                                :kayttajan_org_tyyppi (name (:tyyppi organisaatio))
                                                :sallitut_urakat (if (empty? urakat)
                                                                   ;; Jos ei urakoita, annetaan
                                                                   ;; dummy, jotta IN toimii
                                                                   [-1]
                                                                   urakat)})))))

(defn hae-urakoita [db user teksti]
  (log/debug "Haetaan urakoita tekstihaulla: " teksti)
  (into []
        urakka-xf
        (q/hae-urakoita db (str "%" teksti "%"))))

(defn hae-organisaation-urakat [db user organisaatio-id]
  (log/debug "Haetaan urakat organisaatiolle: " organisaatio-id)
  []
  (into []
        urakka-xf
        (q/hae-organisaation-urakat db organisaatio-id)))

(defn hae-urakan-organisaatio [db user urakka-id]
  (log/debug "Haetaan organisaatio urakalle: " urakka-id)
  (oikeudet/ei-oikeustarkistusta!)
  (let [organisaatio (first (into []
                                  (q/hae-urakan-organisaatio db urakka-id)))]
    (log/debug "Urakan organisaatio saatu: " (pr-str organisaatio))
    organisaatio))

(defn hae-urakan-sopimustyyppi [db user urakka-id]
  (keyword (:sopimustyyppi (first (q/hae-urakan-sopimustyyppi db urakka-id)))))

(defn hae-urakan-tyyppi [db user urakka-id]
  (keyword (:tyyppi (first (q/hae-urakan-tyyppi db urakka-id)))))

(defn tallenna-urakan-sopimustyyppi [db user {:keys [urakka-id sopimustyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/tallenna-urakan-sopimustyyppi! db (name sopimustyyppi) urakka-id)
  (hae-urakan-sopimustyyppi db user urakka-id))

(defn tallenna-urakan-tyyppi [db user {:keys [urakka-id urakkatyyppi]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/tallenna-urakan-tyyppi! db urakkatyyppi urakka-id)
  (hae-urakan-tyyppi db user urakka-id))

(defn hae-yksittainen-urakka [db user urakka-id]
  (log/debug "Hae yksittäinen urakka id:llä: " urakka-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-yleiset user urakka-id)
  (first (into []
               urakka-xf
               (q/hae-yksittainen-urakka db urakka-id))))

(defn aseta-takuun-loppupvm [db user {:keys [urakka-id takuu]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (q/aseta-takuun-loppupvm! db {:urakka urakka-id
                                :loppupvm (:loppupvm takuu)}))

(defn poista-indeksi-kaytosta [db user {:keys [urakka-id]}]
  (when-not (roolit/tilaajan-kayttaja? user)
    (throw (SecurityException. "Vain tilaaja voi poistaa indeksin käytöstä")))

  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-yleiset user urakka-id)
  (jdbc/with-db-transaction [db db]
    (q/aseta-urakan-indeksi! db {:urakka urakka-id :indeksi nil})
    (laskutusyhteenveto-q/poista-urakan-kaikki-muistetut-laskutusyhteenvedot! db
                                                                              {:urakka urakka-id})
    :ok))

(defn- paivita-urakkaa! [db user urakka]
  (log/debug "Päivitetään urakkaa " (::u/nimi urakka))
  (let [hallintayksikko (::u/hallintayksikko urakka)
        urakoitsija (::u/urakoitsija urakka)]
    (q/paivita-harjassa-luotu-urakka<!
      db
      {:id (::u/id urakka)
       :nimi (::u/nimi urakka)
       :urakkanro (::u/urakkanro urakka)
       :alkupvm (::u/alkupvm urakka)
       :loppupvm (::u/loppupvm urakka)
       :alue (::u/alue urakka)
       :hallintayksikko (::o/id hallintayksikko)
       :urakoitsija (::o/id urakoitsija)
       :kayttaja (:id user)})))

(defn- luo-uusi-urakka! [db user {:keys [hanke hallintayksikko urakoitsija] :as urakka}]
  (log/debug "Luodaan uusi urakka " (::u/nimi urakka))
  (let [hanke (hankkeet-palvelu/tallenna-hanke db user {::h/nimi (str (::u/nimi urakka) " H")
                                                        ::h/alkupvm (::u/alkupvm urakka)
                                                        ::h/loppupvm (::u/loppupvm urakka)})
        hallintayksikko (::u/hallintayksikko urakka)
        urakoitsija (::u/urakoitsija urakka)]
    (q/luo-harjassa-luotu-urakka<!
      db
      {:nimi (::u/nimi urakka)
       :urakkanro (::u/urakkanro urakka)
       :alkupvm (::u/alkupvm urakka)
       :loppupvm (::u/loppupvm urakka)
       :alue (::u/alue urakka)
       :hallintayksikko (::o/id hallintayksikko)
       :urakoitsija (::o/id urakoitsija)
       :hanke (::h/id hanke)
       :kayttaja (:id user)})))

(defn- paivita-urakan-sopimukset! [db user urakka sopimukset]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)

    (log/debug "Päivitetään urakan " (::u/nimi urakka) " " (count sopimukset) " sopimusta.")

    (let [urakan-sopimus-idt (map ::s/id (remove :poistettu sopimukset))
          poistettavat-sopimus-idt (map ::s/id (filter :poistettu sopimukset))
          urakan-paasopimus (s/paasopimus sopimukset)
          urakan-sivusopimukset (filter #(not= % urakan-paasopimus) sopimukset)]

      (assert urakan-paasopimus "Urakalla oltava yksi pääsopimus!")

      ;; Irrota poistetut sopimukset urakasta
      (when-not (empty? poistettavat-sopimus-idt)
        (log/debug "Poistetaan urakasta " (::u/id urakka) (count poistettavat-sopimus-idt) " sopimusta.")
        (as-> (sopimukset-q/poista-sopimukset-urakasta! db {:urakka (::u/id urakka)
                                                            :sopimukset poistettavat-sopimus-idt})
              lkm
              (log/debug lkm " sopimusta poistettu onnistuneesti.")))

      ;; Aseta pääsopimus ja aseta muut sopimukset viittaamaan siihen
      ;; Tärkeää tehdä tässä järjestyksessä, koska urakalla saa olla vain yksi pääsopimus
      (log/debug "Asetetaan pääsopimukseksi " (pr-str urakan-paasopimus))
      (sopimukset-q/aseta-sopimuksien-paasopimus! db
                                                  {:sopimukset (map ::s/id urakan-sivusopimukset)
                                                   :paasopimus (::s/id urakan-paasopimus)})
      (sopimukset-q/aseta-sopimus-paasopimukseksi! db
                                                   {:sopimus (::s/id urakan-paasopimus)})

      ;; Liitä annetut sopimukset urakkaan
      (when-not (empty? urakan-sopimus-idt)
        (log/debug "Tallennetaan urakalle " (::u/id urakka) ", " (count urakan-sopimus-idt) " sopimusta.")
        (as-> (sopimukset-q/liita-sopimukset-urakkaan! db {:urakka (::u/id urakka)
                                                           :sopimukset urakan-sopimus-idt})
              lkm
              (log/debug lkm " sopimusta liitetty onnistuneesti."))))))

(defn luo-vv-urakan-toimenpideinstanssit [db urakka]
  (let [params {:urakka_id (::u/id urakka)
                :alkupvm (::u/alkupvm urakka)
                :loppupvm (::u/loppupvm urakka)}]
    ;; TODO Tarttee luoda myös tieto väylätyypistä. Mikä on urakan yhteisten kustannusten väylätyyppi?
    (q/luo-vesivaylaurakan-toimenpideinstanssi<!
      db (merge params {:nimi "Kauppamerenkulun kustannukset TP"
                        :toimenpide_nimi "Kauppamerenkulun kustannukset"}))
    (q/luo-vesivaylaurakan-toimenpideinstanssi<!
      db (merge params {:nimi "Muun vesiliikenteen kustannukset TP"
                        :toimenpide_nimi "Muun vesiliikenteen kustannukset"}))
    (q/luo-vesivaylaurakan-toimenpideinstanssi<!
      db (merge params {:nimi "Urakan yhteiset kustannukset TP"
                        :toimenpide_nimi "Urakan yhteiset kustannukset"}))))

(defn tallenna-vesivaylaurakka [db user urakka]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)

    (jdbc/with-db-transaction [db db]
      (let [sopimukset (::u/sopimukset urakka)
            tallennettu (if (id-olemassa? (::u/id urakka))
                          (paivita-urakkaa! db user urakka)
                          (luo-uusi-urakka! db user urakka))
            urakka (assoc urakka ::u/id (:id tallennettu))]

        (luo-vv-urakan-toimenpideinstanssit db urakka)
        (paivita-urakan-sopimukset! db user urakka sopimukset)
        urakka))))

(defn hae-harjassa-luodut-urakat [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (let [urakat (konv/sarakkeet-vektoriin
                   (into []
                         (comp
                           urakka-xf
                           (map konv/alaviiva->rakenne)
                           (map #(assoc % :hanke (when (get-in % [:hanke :id]) (:hanke %))))
                           (map #(assoc % :urakoitsija (when (get-in % [:urakoitsija :id]) (:urakoitsija %))))
                           (map #(assoc % :hallintayksikko (when (get-in % [:hallintayksikko :id]) (:hallintayksikko %)))))
                         (q/hae-harjassa-luodut-urakat db))
                   {:sopimus :sopimukset
                    :sahkelahetys :sahkelahetykset})]
      (namespacefy urakat {:ns :harja.domain.urakka
                           :inner {:hallintayksikko {:ns :harja.domain.organisaatio}
                                   :urakoitsija {:ns :harja.domain.organisaatio}
                                   :sopimukset {:ns :harja.domain.sopimus}
                                   :hanke {:ns :harja.domain.hanke}}}))))

(defn laheta-urakka-sahkeeseen [sahke user urakka-id]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
    (sahke/laheta-urakka-sahkeeseen sahke urakka-id)))

(defrecord Urakat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           sahke :sahke
           :as this}]
    (julkaise-palvelu http
                      :hallintayksikon-urakat
                      (fn [user hallintayksikko]
                        (hallintayksikon-urakat db user hallintayksikko)))

    (julkaise-palvelu http
                      :hae-urakka
                      (fn [user urakka-id]
                        (hae-yksittainen-urakka db user urakka-id)))

    (julkaise-palvelu http
                      :hae-urakoita
                      (fn [user teksti]
                        (hae-urakoita db user teksti)))

    (julkaise-palvelu http
                      :hae-organisaation-urakat
                      (fn [user organisaatio-id]
                        (hae-organisaation-urakat db user organisaatio-id)))

    (julkaise-palvelu http
                      :hae-urakan-organisaatio
                      (fn [user urakka-id]
                        (hae-urakan-organisaatio db user urakka-id)))

    (julkaise-palvelu http
                      :tallenna-urakan-sopimustyyppi
                      (fn [user tiedot]
                        (tallenna-urakan-sopimustyyppi db user tiedot)))

    (julkaise-palvelu http
                      :tallenna-urakan-tyyppi
                      (fn [user tiedot]
                        (tallenna-urakan-tyyppi db user tiedot)))

    (julkaise-palvelu http
                      :aseta-takuun-loppupvm
                      (fn [user tiedot]
                        (aseta-takuun-loppupvm db user tiedot)))

    (julkaise-palvelu http
                      :poista-indeksi-kaytosta
                      (fn [user tiedot]
                        (poista-indeksi-kaytosta db user tiedot)))

    (julkaise-palvelu http
                      :tallenna-vesivaylaurakka
                      (fn [user tiedot]
                        (tallenna-vesivaylaurakka db user tiedot))
                      {:kysely-spec ::u/tallenna-urakka-kysely
                       :vastaus-spec ::u/tallenna-urakka-vastaus})
    (julkaise-palvelu http
                      :hae-harjassa-luodut-urakat
                      (fn [user _]
                        (hae-harjassa-luodut-urakat db user))
                      {:kysely-spec ::u/hae-harjassa-luodut-urakat-vastaus})

    (julkaise-palvelu http
                      :laheta-urakka-sahkeeseen
                      (fn [user urakka-id]
                        (laheta-urakka-sahkeeseen sahke user urakka-id)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hallintayksikon-urakat
                     :hae-urakka
                     :hae-urakoita
                     :hae-organisaation-urakat
                     :tallenna-urakan-sopimustyyppi
                     :tallenna-urakan-tyyppi
                     :aseta-takuun-loppupvm
                     :tallenna-vesivaylaurakka
                     :hae-harjassa-luodut-urakat
                     :laheta-urakka-sahkeeseen)

    this))
