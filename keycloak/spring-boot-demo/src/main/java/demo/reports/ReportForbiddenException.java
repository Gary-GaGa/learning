package demo.reports;

public class ReportForbiddenException extends RuntimeException {
  public ReportForbiddenException() {
    super("Forbidden");
  }
}
