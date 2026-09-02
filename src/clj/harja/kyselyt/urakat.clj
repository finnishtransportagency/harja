(ns harja.kyselyt.urakat
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/urakat.sql"
  {:positional? true})

(declare urakan-paasopimus-id hae-urakka hae-urakan-tiedot hae-urakan-tyyppi hae-urakan-sopimukset
  hae-urakan-sampo-id hae-yksittainen-urakka hae-urakan-ely hae-urakan-parametrit aseta-tai-paivita-urakkaparametrit
  hae-urakat-tyypilla-ja-hallintayksikolla urakan-hallintayksikko urakan-elinvoimakeskus
  hae-id-sampoidlla hae-urakkatyyppi-sampoidlla aseta-urakan-toimenkuvat
  hae-urakan-velho-oid
  hae-urakan-alkuvuosi onko-olemassa onko-urakalla-tehtavaa hae-urakka-sijainnilla listaa-kaikki-urakat-analytiikalle
  listaa-urakat-analytiikalle-hoitovuosittain hae-paallystysurakat-analytiikalle
  hae-urakkatiedot-laskutusyhteenvetoon perustettu-harjassa? paivita-hankkeen-tiedot-urakalle!
  paivita-urakka-alueiden-nakyma luo-urakka<! paivita-urakka! urakan-paivamaarat hae-urakoiden-geometriat
  hae-lahin-hoidon-alueurakka listaa-urakat-hallintayksikolle listaa-urakat-elinvoimakeskukselle
  hae-urakoita hae-organisaation-urakat
  hae-urakan-organisaatio hae-urakan-sopimustyyppi tallenna-urakan-sopimustyyppi! tallenna-urakan-tyyppi!
  aseta-takuun-loppupvm! tallenna-urakan-projektikansio-linkki! aseta-urakan-kesa-aika! hae-urakan-kesa-aika aseta-urakan-indeksi!
  tallenna-vv-urakkanro<! hae-loytyvat-reimari-turvalaiteryhmat hae-vv-turvalaiteryhmien-nykyiset-urakat
  luo-tai-paivita-vesivaylaurakan-alue<! paivita-harjassa-luotu-urakka<! luo-vesivaylaurakan-toimenpideinstanssi<!
  luo-vesivaylaurakan-toimenpideinstanssin_vaylatyyppi<! luo-harjassa-luotu-urakka<! hae-harjassa-luodut-urakat
  hae-urakan-hoitokaudet aseta-laskutusraja-kaytossa-true! hae-90pv-paattyneet-urakat hae-urakoiden-organisaatiotiedot
  hae-kaikki-urakat-aikavalilla hae-elinvoimakeskuksen-urakat hae-urakoiden-tunnistetiedot
  hae-jarjestelmakayttajan-urakat hae-urakat-ytunnuksella hae-urakat-joihin-jarjestelmalla-erillisoikeus
  hae-elinvoimakeskuksen-kaynnissa-olevat-urakkatyypin-urakat hae-hallintayksikon-kaynnissa-olevat-urakat
  hae-kaynnissa-olevat-urakkatyypin-urakat hae-kaynnissa-olevat-urakat hae-kaynnissa-olevat-hoitourakat)

(defn onko-olemassa? [db id]
  (:exists (first (onko-olemassa db id))))
