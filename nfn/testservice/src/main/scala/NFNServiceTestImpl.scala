import nfn.service._
import nfn.service.CallableNFNService
import nfn.service.NFNName
import nfn.service.value.NFNIntValue
import scala.util.Try

/**
 * Created by basil on 19/02/14.
 */

case class UnitValue() extends value.NFNValue {
  override def toValueName: NFNName = NFNName("Unit")

  override def toNFNName: NFNName = NFNName("Unit")
}

/*
 * Example of a NFNService implementation
 * To add a service to NFN, implement the NFNService interface and
 * add the jar to the local NFNInterface.
 */
class NFNServiceTestImpl extends NFNService {
//  override def exec = println("Test NFNService class loaded successfully!")
  override def parse(unparsedName: String, unparsedValues: Seq[String]): CallableNFNService = {
    val testFun = { (values: Seq[value.NFNValue]) => Try(UnitValue()) }
    CallableNFNService(NFNName(unparsedName), Seq(), testFun)
  }
  override def ccnName: NFNName = NFNName("")
}


case class ChfToDollar() extends NFNService {

  override def parse(unparsedName: String, unparsedValues: Seq[String]): CallableNFNService = {
    val values = unparsedValues match {
      case Seq(chfValueString) => Seq(NFNIntValue(chfValueString.toInt))
      case _ => throw new Exception(s"Service $ccnName could not parse single Int value from: '$unparsedValues'")
    }
    val name = NFNName.parse(unparsedName).getOrElse(throw new Exception(s"Service $ccnName could not parse function name '$unparsedName'"))
    assert(name == this.ccnName)

    val function = { (values: Seq[value.NFNValue]) =>
      values match {
        case Seq(chf: NFNIntValue) => {
          Try(NFNIntValue(chf.amount*2))
        }
        case _ => throw new NFNServiceException(s"${this.ccnName} can only be applied to a single NFNIntValue and not $values")
      }

    }
    CallableNFNService(name, values, function)
  }
  override def ccnName: NFNName = NFNName("ChfToDollar/Int/Int")


}


