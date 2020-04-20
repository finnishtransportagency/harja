(ns harja.palvelin.palvelut.yllapitokohteet.paikkaukset
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [specql.op :as op]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.paikkaus :as q]
            [harja.kyselyt.tieverkko :as tv]
            [harja.palvelin.palvelut.yllapitokohteet.viestinta :as viestinta]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as ypk-yleiset]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))

(defn- muodosta-tr-ehdot
  "Muodostetaan where-osio specql:lle, jossa etsitään tierekisteriosoite määrritetyltä väliltä.
   Tässä on aika paljon ehtoja, sillä oletuksena ei ole täydellisesti annettua tr-osiota vaan
   mikä tahansa osa siitä tai osien kombinaatio kelpaa."
  [tr]
  (let [tr (into {} (filter (fn [[_ arvo]] arvo) tr))
        ;; Täytyy luoda yhteinen pohja, koska nämä kaikki ehdot yhdistetään OR:lla. Tätä pohjaa käytetään kolmessa
        ;; ensimmäisessä ehdossa, joissa käsitellään ne keissit, kun aosa tai aet tai molemmat ovat annettuna.
        alun-yhteinen-pohja (cond-> {}
                                    (:loppuosa tr) (assoc ::tierekisteri/losa (op/<= (:loppuosa tr)))
                                    (:loppuetaisyys tr) (assoc ::tierekisteri/let (op/<= (:loppuetaisyys tr)))
                                    (:alkuosa tr) (assoc ::tierekisteri/aosa (op/>= (:alkuosa tr)))
                                    (:numero tr) (assoc ::tierekisteri/tie (:numero tr)))
        ;; Jos molemmat, alkuosa ja alkuetaisyys ovat annettuna, tarvitaan ehdot 1 ja 3. Muutenhan nuo voisi yhdistää.

        ;; Jos alkuosa on annettu ja kannassa oleva tr-osoitteen alkuosa on annettua arvoa isompi, pitää se palauttaa.
        ;; Palautetaan myös ne arvot kannasta, joissa aosa on yhtäsuuri, jos aet ei ole annettu.
        ehto-1 (when (:alkuosa tr)
                 (assoc alun-yhteinen-pohja ::tierekisteri/aosa ((if (:alkuetaisyys tr)
                                                                   op/>
                                                                   op/>=)
                                                                  (:alkuosa tr))))
        ;; Jos alkuetaisyys on annettu, kannasas olevan arvon aet tulee olla yhtäsuuri tai suurempi, jotta sen
        ;; voi palauttaa.
        ehto-2 (when (:alkuetaisyys tr)
                 (assoc alun-yhteinen-pohja ::tierekisteri/aet (op/>= (:alkuetaisyys tr))))
        ;; Jos alkuosa ja alkuetaisyys on annettu, täytyy kannassa olevan arvon aosa olla suurempi kuin määritetyn tai
        ;; kannassa olevan tr-arvon aosa voi olla yhtäsuuri annetun kanssa, mutta silloin myös aet:in pitää olla
        ;; suurempi tai yhtäsuuri. Ensimmäinen näistä kahdesta vaihtoehdosta on määrritelty jo ehto-1:ssä, joten
        ;; tässä ehto-3:ssa otetaan kantaa vain tuohon jälkimmäiseen.
        ehto-3 (when (and (:alkuosa tr) (:alkuetaisyys tr))
                 (assoc alun-yhteinen-pohja ::tierekisteri/aosa (:alkuosa tr)
                                            ::tierekisteri/aet (op/>= (:alkuetaisyys tr))))

        ;; ehdot 4-6 käsittelevät losa:n ja let:n samalla tavalla kuin ehdot 1-3 käsittelevät aosa:n ja aet:in
        lopun-yhteinen-pohja (cond-> {}
                                     (:alkuosa tr) (assoc ::tierekisteri/aosa (op/>= (:alkuosa tr)))
                                     (:alkuetaisyys tr) (assoc ::tierekisteri/aet (op/>= (:alkuetaisyys tr)))
                                     (:loppuosa tr) (assoc ::tierekisteri/losa (op/<= (:loppuosa tr)))
                                     (:numero tr) (assoc ::tierekisteri/tie (:numero tr)))
        ehto-4 (when (:loppuosa tr)
                 (assoc lopun-yhteinen-pohja ::tierekisteri/losa ((if (:loppuetaisyys tr)
                                                                    op/<
                                                                    op/<=)
                                                                   (:loppuosa tr))))
        ehto-5 (when (:loppuetaisyys tr)
                 (assoc lopun-yhteinen-pohja ::tierekisteri/let (op/<= (:loppuetaisyys tr))))
        ehto-6 (when (and (:loppuosa tr) (:loppuetaisyys tr))
                 (assoc lopun-yhteinen-pohja ::tierekisteri/losa (:loppuosa tr)
                                             ::tierekisteri/let (op/<= (:loppuetaisyys tr))))

        ;; ehto-7 on sitä varten, että jos vain tienumero on annettu eikä mitään muuta.
        ehto-7 (when (= '(:numero) (keys tr))
                 {::tierekisteri/tie (:numero tr)})]
    ;; Yhdistetään kaikki ehdot, jotka eivät ole nillejä.
    (apply op/or
           (keep #(when-not (nil? %)
                    {::paikkaus/tierekisteriosoite %})
                 [ehto-1 ehto-2 ehto-3 ehto-4 ehto-5 ehto-6 ehto-7]))))

(defn hae-urakan-paikkauskohteet [db user {:keys [aikavali paikkaus-idt tyomenetelmat] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (::paikkaus/urakka-id tiedot))
  (let [kysely-params-urakka-id (select-keys tiedot #{::paikkaus/urakka-id})
        kysely-params-aika (if (and aikavali
                                    (not (and (nil? (first aikavali)) (nil? (second aikavali)))))
                             (assoc kysely-params-urakka-id ::paikkaus/alkuaika (cond
                                                                                  (and (first aikavali) (not (second aikavali))) (op/>= (first aikavali))
                                                                                  (and (not (first aikavali)) (second aikavali)) (op/<= (second aikavali))
                                                                                  :else (apply op/between aikavali)))
                             kysely-params-urakka-id)
        kysely-params (if (not-empty tyomenetelmat)
                        (assoc kysely-params-aika ::paikkaus/tyomenetelma (op/in tyomenetelmat))
                        kysely-params-aika)
        kysely-params-tieosa (when-let [tr (into {} (filter (fn [[_ arvo]] arvo) (:tr tiedot)))]
                               (when (not (empty? tr))
                                 (muodosta-tr-ehdot tr)))
        kysely-params-paikkaus-idt (when paikkaus-idt
                                     (assoc kysely-params ::paikkaus/paikkauskohde {::paikkaus/id (op/in paikkaus-idt)}))
        hae-paikkaukset (fn [db]
                          (let [paikkaus-materiaalit (q/hae-paikkaukset-materiaalit db kysely-params)
                                paikkaus-paikkauskohde (q/hae-paikkaukset-paikkauskohde db (if kysely-params-paikkaus-idt
                                                                                            kysely-params-paikkaus-idt
                                                                                            kysely-params))
                                paikkaus-tienkohta (q/hae-paikkaukset-tienkohta db (if kysely-params-tieosa
                                                                                     (op/and kysely-params
                                                                                             kysely-params-tieosa)
                                                                                     kysely-params))]
                            (reduce (fn [kayty paikkaus]
                                      ;; jos kaikista hauista löytyy kyseinen id, se kuuluu silloin palauttaa
                                      (let [materiaali (some #(when (= (::paikkaus/id paikkaus) (::paikkaus/id %))
                                                                %)
                                                             paikkaus-materiaalit)
                                            paikkauskohde (some #(when (= (::paikkaus/id paikkaus) (::paikkaus/id %))
                                                                   %)
                                                                paikkaus-paikkauskohde)
                                            tienkohta (some #(when (= (::paikkaus/id paikkaus) (::paikkaus/id %))
                                                               %)
                                                            paikkaus-tienkohta)]
                                        (if (and materiaali paikkauskohde tienkohta)
                                          (conj kayty (merge materiaali paikkauskohde tienkohta))
                                          kayty)))
                                    [] (:paikkaukset (max-key :count
                                                              {:count (count paikkaus-materiaalit)
                                                               :paikkaukset paikkaus-materiaalit}
                                                              {:count (count paikkaus-paikkauskohde)
                                                               :paikkaukset paikkaus-paikkauskohde}
                                                              {:count (count paikkaus-tienkohta)
                                                               :paikkaukset paikkaus-tienkohta})))))]
    (jdbc/with-db-transaction [db db]
      (if (:ensimmainen-haku? tiedot)
        (let [paikkaukset (hae-paikkaukset db)
              paikkauskohteet (q/hae-urakan-paikkauskohteet db (::paikkaus/urakka-id tiedot))
              tyomenetelmat (q/hae-urakan-tyomenetelmat db (::paikkaus/urakka-id tiedot))
              paikkauksien-tiet (distinct (map #(get-in % [::paikkaus/tierekisteriosoite ::tierekisteri/tie]) paikkaukset))
              teiden-pituudet (reduce (fn [kayty tie]
                                        (assoc kayty tie (into {}
                                                               (map (juxt :osa :pituus))
                                                               (tv/hae-osien-pituudet db {:tie tie
                                                                                          :aosa nil :losa nil}))))
                                      {} paikkauksien-tiet)]
          {:paikkaukset paikkaukset
           :paikkauskohteet paikkauskohteet
           :tyomenetelmat tyomenetelmat
           :teiden-pituudet teiden-pituudet})
        {:paikkaukset (hae-paikkaukset db)}))))

(defn hae-paikkausurakan-kustannukset [db user tiedot]
  (assert (not (nil? (::paikkaus/urakka-id tiedot))) "Urakka-id on nil")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-kustannukset user (::paikkaus/urakka-id tiedot))
  (let [kysely-params-template {:alkuosa nil :numero nil :urakka-id nil :loppuaika nil :alkuaika nil
                                :alkuetaisyys nil :loppuetaisyys nil :loppuosa nil :paikkaus-idt nil
                                :tyomenetelmat nil :tyyppi "kokonaishintainen"}
        kysely-params (assoc (merge kysely-params-template (:tr tiedot))
                        :urakka-id (::paikkaus/urakka-id tiedot)
                        :paikkaus-idt (when-let [paikkaus-idt (:paikkaus-idt tiedot)]
                                        (konv/seq->array paikkaus-idt))
                        :alkuaika (first (:aikavali tiedot))
                        :loppuaika (second (:aikavali tiedot))
                        :tyomenetelmat (when (not-empty (:tyomenetelmat tiedot))
                                         (konv/seq->array (:tyomenetelmat tiedot))))
        haetaan-nollalla-paikkauksella? (and (not (nil? (:paikkaus-idt tiedot)))
                                             (empty? (:paikkaus-idt tiedot)))]
    ;; Palautetaan tyhjä lista, jos käyttäjä ei ole valinnut yhtäkään paikkauskohdetta. Jos paikkaus-idt on nil,
    ;; tarkoittaa se, että näkymään juuri tultiin ja ollaan suorittamassa ensimmäistä hakua. Tyhjä lista taasen, että
    ;; käyttäjä on ottanut kaikki paikkauskohteet pois.
    (cond
      (:ensimmainen-haku? tiedot) (jdbc/with-db-transaction [db db]
                                      (let [kustannukset (if haetaan-nollalla-paikkauksella?
                                                []
                                                (into []
                                                      (map konv/alaviiva->rakenne)
                                                      (q/hae-paikkauskohteen-mahdolliset-kustannukset db kysely-params)))
                                            paikkauskohteet (q/hae-urakan-paikkauskohteet db (::paikkaus/urakka-id tiedot))
                                            tyomenetelmat (q/hae-urakan-tyomenetelmat db (::paikkaus/urakka-id tiedot))]
                                        {:kustannukset kustannukset
                                         :paikkauskohteet paikkauskohteet
                                         :tyomenetelmat tyomenetelmat}))
      haetaan-nollalla-paikkauksella? {:kustannukset []}
      :else {:kustannukset (into []
                                 (map konv/alaviiva->rakenne)
                                 (q/hae-paikkauskohteen-mahdolliset-kustannukset db kysely-params))})))

(defn- paivita-paikkaustoteuma! [db user rivi]
  (q/paivita-paikkaustoteuma! db {:hinta (:hinta rivi)
                                  :poistettu (boolean (:poistettu rivi))
                                  :poistaja (when (:poistettu rivi)
                                              (:id user))
                                  :muokkaaja (:id user)
                                  :tie (:tie rivi)
                                  :aosa (:aosa rivi)
                                  :aet (:aet rivi)
                                  :losa (:losa rivi)
                                  :let (:let rivi)
                                  :tyomenetelma (:tyomenetelma rivi)
                                  :valmistumispvm (:valmistumispvm rivi)
                                  :paikkaustoteuma-id (:paikkaustoteuma-id rivi)}))

(defn- luo-paikkaustoteuma! [db user rivi urakka-id]
  (q/luo-paikkaustoteuma! db {:urakka urakka-id
                              :paikkauskohde (:paikkauskohde rivi)
                              :tie (:tie rivi)
                              :aosa (:aosa rivi)
                              :aet (:aet rivi)
                              :losa (:losa rivi)
                              :let (:let rivi)
                              :luoja (:id user)
                              :tyyppi (or (:tyyppi rivi) "kokonaishintainen")
                              :tyomenetelma (:tyomenetelma rivi)
                              :valmistumispvm (:valmistumispvm rivi)
                              :hinta (:hinta rivi)}))

(defn tallenna-paikkauskustannukset!
  [db user {:keys [hakuparametrit rivit] :as tiedot}]
  (assert (some? tiedot) "Tallenna-paikkauskustannukset tietoja puuttuu.")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-kustannukset user (::paikkaus/urakka-id tiedot))
  (jdbc/with-db-transaction [db db]
                            (doseq [rivi rivit]
                              (assert (:tyomenetelma rivi) "Työmenetelmä puuttuu.")
                              (assert (:valmistumispvm rivi) "Valmistumispvm puuttuu.")
                              (assert (:hinta rivi) "Hinta puuttuu.")
                              (ypk-yleiset/vaadi-paikkauskohde-kuuluu-urakkaan db
                                                                               (::paikkaus/urakka-id tiedot)
                                                                               (:paikkauskohde rivi))
                              (if (pos-int? (:paikkaustoteuma-id rivi))
                                (paivita-paikkaustoteuma! db user rivi)
                                (luo-paikkaustoteuma! db
                                                      user
                                                      rivi
                                                      (::paikkaus/urakka-id tiedot))))

                            ;; Palautetaan symmetrisesti käyttäjän hakuehtojen mukaisesti urakan kustannukset
                            (hae-paikkausurakan-kustannukset db user hakuparametrit)))

(defn ilmoita-virheesta-paikkaustiedoissa!
  [db fim email user {::paikkaus/keys [id nimi urakka-id saate pinta-ala-summa massamenekki-summa rivien-lukumaara] :as tiedot}]
  (assert (some? tiedot) "ilmoita-virheesta-paikkaustiedoissa tietoja puuttuu.")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-kustannukset user (::paikkaus/urakka-id tiedot))
  (let [urakka-sampo-id (urakat-q/hae-urakan-sampo-id db urakka-id)
        response (try

                   (viestinta/laheta-sposti-urakoitsijalle-paikkauskohteessa-virhe (merge tiedot
                                                                                          {:email              email
                                                                                           :fim                fim
                                                                                           :urakka-sampo-id    urakka-sampo-id
                                                                                           :pinta-ala-summa    pinta-ala-summa
                                                                                           :massamenekki-summa massamenekki-summa
                                                                                           :rivien-lukumaara   rivien-lukumaara
                                                                                           :saate              saate
                                                                                           :ilmoittaja         user}))
                   (catch Exception e
                     {:virhe "Sähköpostia ei voi lähettää, yritä myöhemmin uudelleen."}))]
    response)
  ;; TODO 2: Onnistuneen lähetyksen jälkeen merkitse tietokantaan että ilmoitus virheestä lähetetty (tila)
  )

(defn merkitse-paikkauskohde-tarkistetuksi!
  [db user {::paikkaus/keys [id nimi urakka-id] :as tiedot}]
  (assert (some? tiedot) "ilmoita-virheesta-paikkaustiedoissa tietoja puuttuu.")
  (log/debug "merkitse-paikkauskohde-tarkistetuksi!, paikkauskohteen id: " id ", kohteen nimi: " nimi "ja  urakka-id" urakka-id)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-kustannukset user (::paikkaus/urakka-id tiedot))
  ;; TODO 1: Lähetä tarkistettu kohde yhaan
  ;; TODO 2: Merkitse paikkauskohde tarkistetuksi (tila)
  )

(defrecord Paikkaukset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          email (:sonja-sahkoposti this)
          fim (:fim this)
          db (:db this)]
      (julkaise-palvelu http :hae-urakan-paikkauskohteet
                        (fn [user tiedot]
                          (hae-urakan-paikkauskohteet db user tiedot))
                        {:kysely-spec ::paikkaus/urakan-paikkauskohteet-kysely
                         :vastaus-spec ::paikkaus/urakan-paikkauskohteet-vastaus})
      (julkaise-palvelu http :hae-paikkausurakan-kustannukset
                        (fn [user tiedot]
                          (hae-paikkausurakan-kustannukset db user tiedot))
                        {:kysely-spec ::paikkaus/paikkausurakan-kustannukset-kysely
                         :vastaus-spec ::paikkaus/paikkausurakan-kustannukset-vastaus})
      (julkaise-palvelu http :tallenna-paikkauskustannukset
                        (fn [user tiedot]
                          (tallenna-paikkauskustannukset! db user tiedot)))
      (julkaise-palvelu http :ilmoita-virheesta-paikkaustiedoissa
                        (fn [user tiedot]
                          (ilmoita-virheesta-paikkaustiedoissa! db fim email user tiedot)))
      (julkaise-palvelu http :merkitse-paikkauskohde-tarkistetuksi
                        (fn [user tiedot]
                          (merkitse-paikkauskohde-tarkistetuksi! db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-paikkauskohteet
      :hae-paikkausurakan-kustannukset
      :tallenna-paikkauskustannukset
      :ilmoita-virheesta-paikkaustiedoissa
      :merkitse-paikkauskohde-tarkistetuksi)
    this))
