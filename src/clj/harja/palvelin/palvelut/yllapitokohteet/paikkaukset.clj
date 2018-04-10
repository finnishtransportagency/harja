(ns harja.palvelin.palvelut.yllapitokohteet.paikkaukset
  (:require [com.stuartsierra.component :as component]
            [specql.op :as op]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.paikkaus :as paikkaus]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.paikkaus :as q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

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

(defn hae-urakan-paikkauskohteet [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (::paikkaus/urakka-id tiedot))
  (let [kysely-params-urakka-id (select-keys tiedot #{::paikkaus/urakka-id})
        kysely-params-tieosa (when-let [tr (into {} (filter (fn [[_ arvo]] arvo) (:tr tiedot)))]
                               (when (not (empty? tr))
                                 (muodosta-tr-ehdot tr)))
        kysely-params-aika (if-let [aikavali (:aikavali tiedot)]
                             (assoc kysely-params-urakka-id ::paikkaus/alkuaika (cond
                                                                                  (and (first aikavali) (not (second aikavali))) (op/>= (first aikavali))
                                                                                  (and (not (first aikavali)) (second aikavali)) (op/<= (second aikavali))
                                                                                  :else (apply op/between aikavali)))
                             kysely-params-urakka-id)
        kysely-params-paikkaus-idt (if-let [paikkaus-idt (:paikkaus-idt tiedot)]
                                     (assoc kysely-params-aika ::paikkaus/paikkauskohde {::paikkaus/id (op/in paikkaus-idt)})
                                     kysely-params-aika)
        kysely-params-tyomenetelmat (if-let [tyomenetelmat (:tyomenetelma tiedot)]
                                      (assoc kysely-params-tyomenetelmat ))
        kysely-params (if kysely-params-tieosa
                        (op/and kysely-params-paikkaus-idt
                                kysely-params-tieosa)
                        kysely-params-paikkaus-idt)]
    (q/hae-paikkaukset db kysely-params)))

(defn hae-paikkausurakan-kustannukset [db user tiedot]
  (assert (not (nil? (::paikkaus/urakka-id tiedot))) "Urakka-id on nil")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat user (::paikkaus/urakka-id tiedot))
  (let [kysely-params-template {:alkuosa nil :numero nil :urakka-id nil :loppuaika nil :alkuaika nil
                                :alkuetaisyys nil :loppuetaisyys nil :loppuosa nil :paikkaus-idt nil}]
    ;; Palautetaan tyhjä lista, jos käyttäjä ei ole valinnut yhtäkään paikkauskohdetta. Jos paikkaus-idt on nil,
    ;; tarkoittaa se, että näkymään juuri tultiin ja ollaan suorittamassa ensimmäistä hakua. Tyhjä lista taasen, että
    ;; käyttäjä on ottanut kaikki paikkauskohteet pois.
    (if (and (not (nil? (:paikkaus-idt tiedot)))
             (empty? (:paikkaus-idt tiedot)))
      []
      (q/hae-paikkaustoteumat-tierekisteriosoitteella db (assoc (merge kysely-params-template (:tr tiedot))
                                                           :urakka-id (::paikkaus/urakka-id tiedot)
                                                           :paikkaus-idt (when (:paikkaus-idt tiedot) (str "{" (apply str (interpose "," (:paikkaus-idt tiedot))) "}"))
                                                           :alkuaika (first (:aikavali tiedot))
                                                           :loppuaika (second (:aikavali tiedot)))))))

(defrecord Paikkaukset []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
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
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-paikkauskohteet
      :hae-paikkausurakan-kustannukset)
    this))